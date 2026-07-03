#!/usr/bin/env python3
"""Autonomous HTML5 game fetcher for Offline Game Hub.

The bot searches GitHub for small, permissively licensed HTML5 games, validates that
one can run offline in an Android WebView, vendors same-repository CSS/JS into a
single ``index.html``, and writes it under ``games/<slug>/`` with a placeholder
``logo.png``.

It is intentionally conservative: if a candidate depends on CDNs, heavyweight
frameworks, remote assets, modules, or unavailable local files, the candidate is
skipped rather than committing a broken game.
"""
from __future__ import annotations

import base64
import hashlib
import os
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
from urllib.parse import quote, urljoin, urlparse

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent
GAMES_DIR = ROOT / "games"
CATALOG_FILE = ROOT / "games.json"
USER_AGENT = "offline-game-hub-auto-fetcher/1.0 (+https://github.com/)"
MAX_HTML_BYTES = 350_000
MAX_ASSET_BYTES = 250_000
REQUEST_TIMEOUT = 20
ALLOWED_LICENSES = {
    "mit",
    "apache-2.0",
    "bsd-2-clause",
    "bsd-3-clause",
    "isc",
    "unlicense",
    "0bsd",
    "cc0-1.0",
}
HEAVY_OR_ONLINE_MARKERS = re.compile(
    r"(jquery|react|vue|angular|phaser|pixi|three\.js|babylon|matter\.js|"
    r"bootstrap|tailwind|firebase|googleapis|gstatic|cdnjs|unpkg|jsdelivr|"
    r"ajax\.googleapis|analytics|gtag|adsbygoogle|websocket|socket\.io)",
    re.IGNORECASE,
)
GAME_MARKERS = re.compile(
    r"(canvas|getContext|requestAnimationFrame|keydown|keyup|touchstart|"
    r"score|gameOver|collision|player|enemy)",
    re.IGNORECASE,
)

# A tiny valid PNG (1x1 transparent pixel). The catalog only requires the file to exist.
PLACEHOLDER_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/"
    "p9sAAAAASUVORK5CYII="
)


@dataclass(frozen=True)
class Candidate:
    repo: str
    branch: str
    path: str
    html_url: str
    raw_url: str
    license_key: str

    @property
    def base_raw_url(self) -> str:
        parent = str(Path(self.path).parent).replace(".", "").strip("/")
        base = f"https://raw.githubusercontent.com/{self.repo}/{self.branch}/"
        return urljoin(base, f"{parent}/" if parent else "")


def log(message: str) -> None:
    print(f"[auto-game-bot] {message}", flush=True)


def github_headers() -> dict[str, str]:
    headers = {"Accept": "application/vnd.github+json", "User-Agent": USER_AGENT}
    token = os.getenv("GITHUB_TOKEN") or os.getenv("GH_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def http_get(url: str, *, raw: bool = False) -> requests.Response:
    headers = {"User-Agent": USER_AGENT}
    if "api.github.com" in url:
        headers = github_headers()
    response = requests.get(url, headers=headers, timeout=REQUEST_TIMEOUT)
    response.raise_for_status()
    if raw and len(response.content) > MAX_ASSET_BYTES:
        raise ValueError(f"asset too large: {url}")
    return response


def existing_game_slugs() -> set[str]:
    GAMES_DIR.mkdir(exist_ok=True)
    return {p.name.lower() for p in GAMES_DIR.iterdir() if p.is_dir()}


def slugify(name: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return slug[:60] or "html5-game"


def unique_slug(base: str, used: set[str]) -> str:
    slug = slugify(base)
    if slug not in used:
        return slug
    suffix = hashlib.sha1(base.encode("utf-8")).hexdigest()[:7]
    return f"{slug[:50]}-{suffix}"


def github_search_candidates() -> Iterable[Candidate]:
    """Yield code-search results that look like standalone HTML5 games."""
    queries = [
        'filename:index.html "requestAnimationFrame" "canvas" "game" size:<250000',
        'filename:index.html "touchstart" "score" "canvas" size:<250000',
        'filename:index.html "keydown" "Game Over" "canvas" size:<250000',
    ]
    seen: set[str] = set()
    for query in queries:
        url = f"https://api.github.com/search/code?q={quote(query)}&per_page=20"
        try:
            items = http_get(url).json().get("items", [])
        except Exception as exc:  # Keep the workflow alive when search is rate-limited.
            log(f"GitHub search skipped for query {query!r}: {exc}")
            continue
        for item in items:
            repo_name = item.get("repository", {}).get("full_name")
            path = item.get("path", "")
            if not repo_name or not path or (repo_name, path) in seen:
                continue
            seen.add((repo_name, path))
            try:
                repo_api = http_get(f"https://api.github.com/repos/{repo_name}").json()
                license_key = (repo_api.get("license") or {}).get("key", "").lower()
                if license_key not in ALLOWED_LICENSES:
                    log(f"skip {repo_name}: unsupported or missing license {license_key!r}")
                    continue
                branch = repo_api.get("default_branch") or "main"
                raw_url = f"https://raw.githubusercontent.com/{repo_name}/{branch}/{path}"
                yield Candidate(repo_name, branch, path, item.get("html_url", raw_url), raw_url, license_key)
            except Exception as exc:
                log(f"candidate metadata failed for {repo_name}: {exc}")


def is_external(url: str) -> bool:
    parsed = urlparse(url)
    return bool(parsed.scheme in {"http", "https", "//"} or parsed.netloc)


def fetch_same_repo_asset(candidate: Candidate, src: str) -> str:
    clean = src.split("#", 1)[0].split("?", 1)[0]
    if is_external(clean) or clean.startswith(("/", "data:", "mailto:", "tel:")):
        raise ValueError(f"external/absolute asset rejected: {src}")
    if ".." in Path(clean).parts:
        raise ValueError(f"parent traversal rejected: {src}")
    asset_url = urljoin(candidate.base_raw_url, clean)
    response = http_get(asset_url, raw=True)
    return response.text


def build_offline_html(candidate: Candidate) -> str | None:
    try:
        response = http_get(candidate.raw_url)
        if len(response.content) > MAX_HTML_BYTES:
            log(f"skip {candidate.repo}: HTML too large")
            return None
        html = response.text
        if HEAVY_OR_ONLINE_MARKERS.search(html) or not GAME_MARKERS.search(html):
            log(f"skip {candidate.repo}: not a lightweight offline game")
            return None

        soup = BeautifulSoup(html, "html.parser")

        # Reject remote media/images/fonts because Android WebView must work offline.
        for tag in soup.find_all(["img", "audio", "video", "source", "iframe", "embed", "object"]):
            src = tag.get("src") or tag.get("data")
            if src and not src.startswith("data:"):
                raise ValueError(f"non-inline media asset rejected: {src}")

        for link in list(soup.find_all("link")):
            rel = " ".join(link.get("rel", [])).lower()
            href = link.get("href")
            if "stylesheet" in rel and href:
                css = fetch_same_repo_asset(candidate, href)
                if HEAVY_OR_ONLINE_MARKERS.search(css):
                    raise ValueError("CSS contains online/heavy dependency marker")
                style = soup.new_tag("style")
                style.string = f"\n/* Inlined from {href} */\n{css}\n"
                link.replace_with(style)
            else:
                link.decompose()

        for script in list(soup.find_all("script")):
            src = script.get("src")
            if src:
                js = fetch_same_repo_asset(candidate, src)
                if HEAVY_OR_ONLINE_MARKERS.search(js):
                    raise ValueError("JS contains online/heavy dependency marker")
                new_script = soup.new_tag("script")
                new_script.string = f"\n/* Inlined from {src} */\n{js}\n"
                script.replace_with(new_script)
            elif script.string and HEAVY_OR_ONLINE_MARKERS.search(script.string):
                raise ValueError("inline JS contains online/heavy dependency marker")

        final_html = str(soup)
        if HEAVY_OR_ONLINE_MARKERS.search(final_html):
            raise ValueError("final HTML still contains online/heavy dependency marker")
        if not GAME_MARKERS.search(final_html):
            raise ValueError("final HTML no longer looks game-like")
        return "<!-- Fetched by auto_game_bot.py from " + candidate.html_url + " -->\n" + final_html
    except Exception as exc:
        log(f"skip {candidate.repo}/{candidate.path}: {exc}")
        return None


def write_game(candidate: Candidate, html: str, used: set[str]) -> Path:
    repo_part = candidate.repo.split("/", 1)[-1]
    name = Path(candidate.path).parent.name if Path(candidate.path).parent.name else repo_part
    slug = unique_slug(name, used)
    target = GAMES_DIR / slug
    target.mkdir(parents=True, exist_ok=False)
    (target / "index.html").write_text(html, encoding="utf-8")
    (target / "logo.png").write_bytes(PLACEHOLDER_PNG)
    (target / "SOURCE.txt").write_text(
        f"Source: {candidate.html_url}\nRepository: {candidate.repo}\nLicense: {candidate.license_key}\nFetched: {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}\n",
        encoding="utf-8",
    )
    return target


def main() -> int:
    used = existing_game_slugs()
    try:
        for candidate in github_search_candidates():
            html = build_offline_html(candidate)
            if not html:
                continue
            try:
                target = write_game(candidate, html, used)
                log(f"added {target.relative_to(ROOT)} from {candidate.html_url}")
                return 0
            except FileExistsError:
                # Another run may have created the folder between slug calculation and write.
                log(f"target folder already exists for {candidate.repo}; trying next candidate")
                continue
            except Exception as exc:
                log(f"failed to write candidate {candidate.repo}: {exc}")
                continue
        log("no suitable new game found")
        return 0
    except Exception as exc:  # Last-resort guard: scheduled workflows should not crash noisily.
        log(f"unexpected non-fatal error: {exc}")
        return 0


if __name__ == "__main__":
    sys.exit(main())
