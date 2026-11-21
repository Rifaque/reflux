"""Checks the repository's Markdown links: every relative link reaches a file, and every #anchor a heading
(GitHub's heading slugs) or an explicit <a id> / {#id}. External links are not fetched.

    python -I tools/docs/check_links.py [repo]        exit 1 when a link is broken

Skips build output, dependencies, and the website (its own check-links covers the built site).
"""
import pathlib
import re
import sys
import urllib.parse

SKIP = {".git", "node_modules", "build", "dist", "dist-server", ".gradle", "test-results", "website", ".reflux",
        "brag-output", "store-assets"}
LINK = re.compile(r"(?<!\!)\[(?:[^\]\[]|\[[^\]]*\])*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
IMAGE = re.compile(r"!\[[^\]]*\]\(([^)\s]+)\)")
HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
EXPLICIT = re.compile(r"<a\s+(?:id|name)=\"([^\"]+)\"")


def slug(text):
    """GitHub's slug: lower case, markup and punctuation dropped (hyphens and underscores kept), spaces to hyphens."""
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)  # a link in a heading keeps its text
    text = text.replace("`", "").lower()
    text = re.sub(r"[^\w\- ]", "", text, flags=re.UNICODE)
    return text.replace(" ", "-")


def anchors(path, cache={}):  # noqa: B006 - a deliberate per-run cache
    if path not in cache:
        found, seen, fence = set(), {}, False
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.lstrip().startswith(("```", "~~~")):
                fence = not fence
                continue
            if fence:
                continue
            found.update(EXPLICIT.findall(line))
            m = HEADING.match(line)
            if m:
                base = slug(m.group(2))
                n = seen.get(base, 0)
                seen[base] = n + 1
                found.add(base if n == 0 else f"{base}-{n}")
        cache[path] = found
    return cache[path]


def files(root):
    for p in root.rglob("*.md"):
        if not any(part in SKIP for part in p.relative_to(root).parts):
            yield p


def strip_code(text):
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    text = re.sub(r"~~~.*?~~~", "", text, flags=re.S)
    return re.sub(r"`[^`\n]*`", "", text)


def main():
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    broken, checked = [], 0
    for md in sorted(files(root)):
        text = strip_code(md.read_text(encoding="utf-8", errors="replace"))
        for target in LINK.findall(text) + IMAGE.findall(text):
            if re.match(r"^[a-z][a-z0-9+.-]*:", target, re.I):  # http:, https:, mailto: ...
                continue
            checked += 1
            raw, _, frag = target.partition("#")
            raw = urllib.parse.unquote(raw)
            dest = (md.parent / raw).resolve() if raw else md
            rel = md.relative_to(root).as_posix()
            if not dest.exists():
                broken.append(f"{rel}: {target} (no such file)")
                continue
            if frag and dest.suffix == ".md" and urllib.parse.unquote(frag).lower() not in {a.lower() for a in anchors(dest)}:
                broken.append(f"{rel}: {target} (no such heading)")
    for b in broken:
        print(b)
    print(f"{checked} relative links checked, {len(broken)} broken")
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
