#!/usr/bin/env python3
"""Build zipped HTML game packages and a games.json catalog for Offline Game Hub."""
from __future__ import annotations

import json
import os
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent
GAMES_DIR = ROOT / "games"
RELEASES_DIR = ROOT / "releases"
CATALOG_FILE = ROOT / "games.json"
LOGO_NAMES = ("logo.png", "logo.jpg", "logo.jpeg", "logo.webp", "icon.png", "thumbnail.png", "cover.png")


def fail(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)
    raise SystemExit(1)


def slugify(value: str) -> str:
    slug = re.sub(r"[^a-z0-9_-]+", "-", value.strip().lower())
    return re.sub(r"-+", "-", slug).strip("-")


def title_from_id(game_id: str) -> str:
    return re.sub(r"[-_]+", " ", game_id).strip().title() or game_id


def read_metadata(folder: Path, game_id: str) -> dict[str, Any]:
    metadata_path = folder / "game.json"
    metadata: dict[str, Any] = {}
    if metadata_path.exists():
        try:
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            fail(f"JSON parsing failed in {metadata_path}: line {exc.lineno}, column {exc.colno}: {exc.msg}")
    return {
        "id": str(metadata.get("id") or game_id),
        "name": str(metadata.get("name") or title_from_id(game_id)),
        "description": str(metadata.get("description") or "Offline HTML game"),
        "version": int(metadata.get("version") or 1),
    }


def raw_url(owner_repo: str, branch: str, relative: Path) -> str:
    encoded = "/".join(quote(part) for part in relative.as_posix().split("/"))
    return f"https://raw.githubusercontent.com/{owner_repo}/{branch}/{encoded}"


def find_logo(folder: Path) -> Path | None:
    lower_map = {p.name.lower(): p for p in folder.iterdir() if p.is_file()}
    for name in LOGO_NAMES:
        if name in lower_map:
            return lower_map[name]
    for p in folder.iterdir():
        if p.is_file() and p.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}:
            return p
    return None


def zip_game(folder: Path, zip_path: Path) -> None:
    with tempfile.NamedTemporaryFile(delete=False, suffix=".zip", dir=str(RELEASES_DIR)) as tmp:
        tmp_path = Path(tmp.name)
    try:
        with zipfile.ZipFile(tmp_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            files = sorted(p for p in folder.rglob("*") if p.is_file())
            if not files:
                raise ValueError(f"{folder} has no files to package")
            for file_path in files:
                archive.write(file_path, file_path.relative_to(folder).as_posix())
        tmp_path.replace(zip_path)
    finally:
        tmp_path.unlink(missing_ok=True)


def main() -> None:
    owner_repo = os.environ.get("GITHUB_REPOSITORY")
    publish_branch = os.environ.get("PUBLISH_BRANCH", "gh-pages")
    if not owner_repo:
        owner_repo = os.environ.get("CATALOG_REPOSITORY", "YOUR_USERNAME/YOUR_REPO")
        print(f"GITHUB_REPOSITORY not set; using {owner_repo}")

    if not GAMES_DIR.exists():
        fail("Missing required /games directory at repository root")

    RELEASES_DIR.mkdir(parents=True, exist_ok=True)
    for old_zip in RELEASES_DIR.glob("*.zip"):
        old_zip.unlink()

    catalog: list[dict[str, Any]] = []
    for folder in sorted(p for p in GAMES_DIR.iterdir() if p.is_dir()):
        game_id = slugify(folder.name)
        if not game_id:
            print(f"Skipping folder with invalid name: {folder}", file=sys.stderr)
            continue
        if not (folder / "index.html").exists():
            print(f"Skipping {folder}: missing index.html", file=sys.stderr)
            continue

        metadata = read_metadata(folder, game_id)
        metadata["id"] = slugify(metadata["id"])
        zip_name = f"{metadata['id']}.zip"
        zip_path = RELEASES_DIR / zip_name
        zip_game(folder, zip_path)
        logo = find_logo(folder)

        catalog.append({
            "id": metadata["id"],
            "name": metadata["name"],
            "description": metadata["description"],
            "version": metadata["version"],
            "logoUrl": raw_url(owner_repo, publish_branch, logo.relative_to(ROOT)) if logo else "",
            "downloadUrl": raw_url(owner_repo, publish_branch, zip_path.relative_to(ROOT)),
            "sizeBytes": zip_path.stat().st_size,
        })

    CATALOG_FILE.write_text(json.dumps({"games": catalog}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Generated {len(catalog)} games in {CATALOG_FILE} and {RELEASES_DIR}")


if __name__ == "__main__":
    try:
        main()
    except OSError as exc:
        fail(f"File processing failed: {exc}")
    except ValueError as exc:
        fail(str(exc))
