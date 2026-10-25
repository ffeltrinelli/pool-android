#!/usr/bin/env python3
"""Copy the curated gallery from a freshly generated visual smoke report."""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = {
    "gallery-splash": "splash.png",
    "gallery-top": "top-view.png",
    "gallery-player": "player-view-start.png",
    "gallery-settings": "settings.png",
    "gallery-help": "help.png",
    "gallery-placement": "placement.png",
    "gallery-result": "result.png",
    "replay-close": "replay-close.png",
    "replay-overview": "replay-overview.png",
    "replay-diagonal": "replay-diagonal.png",
    "replay-hold": "replay-hold.png",
}


def main():
    source = ROOT / "build/reports/visual"
    destination = ROOT / "docs/images"
    missing = [name for name in SCENARIOS if not (source / f"{name}.png").is_file()]
    if missing:
        raise SystemExit("Run :lwjgl3:visualTest first; missing captures: " + ", ".join(missing))
    destination.mkdir(parents=True, exist_ok=True)
    for scenario, filename in SCENARIOS.items():
        shutil.copyfile(source / f"{scenario}.png", destination / filename)
    print(f"Updated {len(SCENARIOS)} gallery images. Inspect them before committing.")


if __name__ == "__main__":
    main()
