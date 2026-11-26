"""Self-test of fetch_libmpv_sources.py against a local HTTP server: only a file with the listed SHA-256 is ever kept,
whatever the server sends, and the log says what was sent without leaking query strings.

    python3 -I -m unittest discover -s tools/ci -p 'test_*.py'
"""
import contextlib
import hashlib
import http.server
import io
import os
import pathlib
import shutil
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import fetch_libmpv_sources as fetcher  # noqa: E402

GOOD = b"\x1f\x8b" + bytes(range(256)) * 64
GOOD_SHA = hashlib.sha256(GOOD).hexdigest()
HTML = b"<!DOCTYPE html><html><head><title>Just a moment</title></head><body>please wait</body></html>"


class Handler(http.server.BaseHTTPRequestHandler):
    hits = {}

    def log_message(self, *args):
        pass

    def send(self, status, body, content_type="application/x-gzip", length=None):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body) if length is None else length))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        Handler.hits[path] = Handler.hits.get(path, 0) + 1
        n = Handler.hits[path]
        if path == "/good.tar.gz":
            self.send(200, GOOD)
        elif path == "/html.tar.gz":
            self.send(200, HTML, "text/html; charset=utf-8")
        elif path == "/flaky.tar.gz":
            self.send(200, HTML if n == 1 else GOOD, "text/html" if n == 1 else "application/x-gzip")
        elif path == "/missing.tar.gz":
            self.send(404, b"not found", "text/plain")
        elif path == "/redirect.tar.gz":
            self.send_response(302)
            self.send_header("Location", "/good.tar.gz?X-Amz-Signature=SECRETSIGNATURE")
            self.end_headers()
        elif path == "/redirect-html.tar.gz":
            self.send_response(302)
            self.send_header("Location", "/html.tar.gz?X-Amz-Signature=SECRETSIGNATURE")
            self.end_headers()
        elif path == "/truncated.tar.gz":
            self.send(200, GOOD[:1000], length=len(GOOD))
            self.close_connection = True
        else:
            self.send(404, b"", "text/plain")


class FetchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.base = f"http://127.0.0.1:{cls.server.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        Handler.hits.clear()
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = pathlib.Path(self.tmp.name, "cache")
        self.addCleanup(self.tmp.cleanup)

    def run_fetch(self, path, sha=GOOD_SHA, attempts=3):
        listing = pathlib.Path(self.tmp.name, "sources.txt")
        listing.write_text(f"# comment\n\nzlib 1.3.1 {sha} {self.base}{path}\n")
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            code = fetcher.main([str(listing), str(self.dir), "--attempts", str(attempts), "--pause", "0"])
        return code, out.getvalue()

    def files(self):
        return sorted(os.listdir(self.dir)) if self.dir.exists() else []

    def assertOnlyTheVerifiedArchive(self):
        self.assertEqual(self.files(), ["zlib-1.3.1.tar.gz"])
        self.assertEqual(hashlib.sha256((self.dir / "zlib-1.3.1.tar.gz").read_bytes()).hexdigest(), GOOD_SHA)

    def assertNothingKept(self):
        self.assertEqual(self.files(), [], "no rejected file, part file or header file may be left behind")

    def test_a_matching_archive_is_kept_under_the_name_build_sh_reads(self):
        code, log = self.run_fetch("/good.tar.gz")
        self.assertEqual(code, 0, log)
        self.assertOnlyTheVerifiedArchive()
        self.assertIn("downloaded and verified", log)

    def test_an_html_page_sent_as_200_is_rejected_every_time_and_described(self):
        code, log = self.run_fetch("/html.tar.gz")
        self.assertEqual(code, 1)
        self.assertNothingKept()
        self.assertEqual(Handler.hits["/html.tar.gz"], 3, "attempts are bounded")
        self.assertEqual(log.count("REJECTED and deleted"), 3)
        for detail in ("HTTP status 200", "content-type text/html", f"received {len(HTML)} bytes",
                       hashlib.sha256(HTML).hexdigest(), "<!DOCTYPE html>", f"expected {GOOD_SHA}"):
            self.assertIn(detail, log)
        self.assertIn("no attempt matched the listed SHA-256 for: zlib", log)

    def test_a_wrong_response_then_a_right_one_keeps_only_the_right_one(self):
        code, log = self.run_fetch("/flaky.tar.gz")
        self.assertEqual(code, 0, log)
        self.assertOnlyTheVerifiedArchive()
        self.assertIn("attempt 1 of 3 REJECTED", log)
        self.assertIn("attempt 2)", log)

    def test_a_404_is_rejected(self):
        code, log = self.run_fetch("/missing.tar.gz", attempts=2)
        self.assertEqual(code, 1)
        self.assertNothingKept()
        self.assertIn("HTTP status 404", log)

    def test_a_truncated_transfer_is_rejected(self):
        code, log = self.run_fetch("/truncated.tar.gz", attempts=1)
        self.assertEqual(code, 1)
        self.assertNothingKept()
        self.assertIn("REJECTED", log)

    def test_a_listed_digest_that_nothing_matches_fails_even_for_a_good_server(self):
        code, log = self.run_fetch("/good.tar.gz", sha="0" * 64, attempts=2)
        self.assertEqual(code, 1)
        self.assertNothingKept()

    def test_a_corrupt_file_already_present_is_replaced(self):
        self.dir.mkdir(parents=True)
        (self.dir / "zlib-1.3.1.tar.gz").write_bytes(HTML)
        code, log = self.run_fetch("/good.tar.gz")
        self.assertEqual(code, 0, log)
        self.assertOnlyTheVerifiedArchive()
        self.assertIn("present but SHA-256", log)

    def test_a_verified_file_already_present_is_not_downloaded_again(self):
        self.dir.mkdir(parents=True)
        (self.dir / "zlib-1.3.1.tar.gz").write_bytes(GOOD)
        code, log = self.run_fetch("/good.tar.gz")
        self.assertEqual(code, 0, log)
        self.assertEqual(Handler.hits, {})
        self.assertIn("present and verified", log)

    def test_a_redirect_to_a_signed_url_is_followed_and_its_query_never_logged(self):
        code, log = self.run_fetch("/redirect.tar.gz")
        self.assertEqual(code, 0, log)
        self.assertOnlyTheVerifiedArchive()
        shutil.rmtree(self.dir)
        code, log = self.run_fetch("/redirect-html.tar.gz", attempts=1)
        self.assertEqual(code, 1)
        self.assertNotIn("SECRET", log)
        self.assertIn("/html.tar.gz (query removed)", log)
        self.assertIn("redirects 1", log)

    def test_the_real_listing_is_read_as_build_sh_reads_it(self):
        listing = pathlib.Path(__file__).resolve().parents[2] / "tools" / "libmpv" / "sources.txt"
        names = [file for *_, file in fetcher.entries(listing)]
        self.assertIn("zlib-1.3.1.tar.gz", names)
        self.assertEqual(len(names), len(set(names)))
        for name, version, sha, url, file in fetcher.entries(listing):
            self.assertRegex(sha, "^[0-9a-f]{64}$")
            self.assertTrue(file.startswith(f"{name}-{version}."))


if __name__ == "__main__":
    unittest.main()
