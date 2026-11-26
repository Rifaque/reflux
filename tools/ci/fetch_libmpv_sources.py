"""Fetches every archive of tools/libmpv/sources.txt into a directory that the libmpv build mounts read-only as /cache,
keeping a file only when its SHA-256 is the one listed there (docs/engineering/CI-PERFORMANCE-AUDIT.md, L5).

    python3 -I tools/ci/fetch_libmpv_sources.py <sources.txt> <directory> [--attempts N] [--pause SECONDS]

The listed digest is the only acceptance test: a file with any other digest is deleted and downloaded again from the
same URL, at most N times in all, and the run fails if no attempt matches. A file already in the directory (a restored
cache) is checked the same way and replaced if it does not match. Nothing here changes what is built: tools/libmpv/build.sh
finds each archive under the name it expects and verifies its digest again before unpacking it.

On a mismatch it logs what the server sent: the status, the final URL and redirect count, the content type and length,
the size received, the digest, and the first bytes made printable. URLs are logged without their query strings (signed
download links carry credentials there); no request carries a credential.
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.parse


def entries(listing):
    """(name, version, sha256, url, file name) per archive, exactly as build.sh reads and names them."""
    with open(listing, encoding="utf-8") as f:
        lines = f.read().splitlines()
    for raw in lines:
        parts = raw.split()
        if not parts or parts[0].startswith("#"):
            continue
        name, version, sha, url = parts[:4]
        if url.endswith(".tar.gz"):
            ext = "tar.gz"
        elif url.endswith(".tar.xz"):
            ext = "tar.xz"
        else:
            sys.exit(f"unknown archive type: {safe_url(url)}")
        yield name, version, sha.lower(), url, f"{name}-{version}.{ext}"


def safe_url(url):
    parts = urllib.parse.urlsplit(url)
    host = (parts.hostname or "") + (f":{parts.port}" if parts.port else "")  # never user:password@
    shown = urllib.parse.urlunsplit((parts.scheme, host, parts.path, "", ""))
    return shown + (" (query removed)" if parts.query else "")


def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def printable(data):
    return "".join(chr(b) if 32 <= b < 127 else "." for b in data)


def download(url, target):
    """One attempt with curl (the client build.sh uses), following redirects; returns what the server sent."""
    headers = target + ".headers"
    write_out = "%{json}"
    proc = subprocess.run(
        ["curl", "-sS", "-L", "--max-redirs", "10", "--connect-timeout", "30", "--max-time", "600",
         "-o", target, "-D", headers, "-w", write_out, url],
        capture_output=True, text=True,
    )
    info = {}
    try:
        info = json.loads(proc.stdout or "{}")
    except json.JSONDecodeError:
        pass
    length = None
    try:
        # The last response's Content-Length (after any redirect).
        for line in open(headers, encoding="latin-1"):
            if line.lower().startswith("content-length:"):
                length = line.split(":", 1)[1].strip()
            elif line.startswith("HTTP/"):
                length = None
    except OSError:
        pass
    finally:
        if os.path.exists(headers):
            os.remove(headers)
    return {
        "curl_exit": proc.returncode,
        "curl_error": proc.stderr.strip(),
        "status": info.get("response_code"),
        "final_url": safe_url(info["url_effective"]) if info.get("url_effective") else None,
        "redirects": info.get("num_redirects"),
        "content_type": info.get("content_type"),
        "content_length": length,
    }


def fetch(name, sha, url, path, attempts, pause, log):
    """True once path holds the archive with the listed digest; never leaves any other file at path."""
    if os.path.exists(path):
        actual = digest(path)
        if actual == sha:
            log(f"{name}: present and verified ({os.path.getsize(path)} bytes)")
            return True
        log(f"{name}: present but SHA-256 {actual}, expected {sha}: deleted")
        os.remove(path)
    for attempt in range(1, attempts + 1):
        part = path + ".part"
        if os.path.exists(part):
            os.remove(part)
        sent = download(url, part)
        size = os.path.getsize(part) if os.path.exists(part) else 0
        actual = digest(part) if size else None
        if sent["curl_exit"] == 0 and sent["status"] == 200 and actual == sha:
            os.replace(part, path)
            log(f"{name}: downloaded and verified ({size} bytes, attempt {attempt})")
            return True
        head = b""
        if size:
            with open(part, "rb") as f:
                head = f.read(48)
        if os.path.exists(part):
            os.remove(part)
        log(f"{name}: attempt {attempt} of {attempts} REJECTED and deleted: url {safe_url(url)}; "
            f"final url {sent['final_url']}; redirects {sent['redirects']}; HTTP status {sent['status']}; "
            f"content-type {sent['content_type']}; content-length {sent['content_length']}; received {size} bytes; "
            f"SHA-256 {actual}, expected {sha}; first bytes {printable(head)!r}"
            + (f"; curl exit {sent['curl_exit']}: {sent['curl_error']}" if sent["curl_exit"] else ""))
        if attempt < attempts:
            time.sleep(pause * attempt)
    return False


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("listing")
    parser.add_argument("directory")
    parser.add_argument("--attempts", type=int, default=3)
    parser.add_argument("--pause", type=float, default=10.0, help="seconds before the second attempt; doubled after")
    args = parser.parse_args(argv)
    if args.attempts < 1:
        parser.error("--attempts must be at least 1")
    os.makedirs(args.directory, exist_ok=True)
    log = lambda message: print(message, flush=True)  # noqa: E731
    failed = [name for name, _, sha, url, file in entries(args.listing)
              if not fetch(name, sha, url, os.path.join(args.directory, file), args.attempts, args.pause, log)]
    leftovers = [f for f in os.listdir(args.directory) if f.endswith((".part", ".headers"))]
    for f in leftovers:
        os.remove(os.path.join(args.directory, f))
    if failed:
        print(f"no attempt matched the listed SHA-256 for: {', '.join(failed)}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
