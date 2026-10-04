#!/usr/bin/env python3
"""
Lani Session End Hook
Creates daily backups and displays session summary
"""
import json
import shutil
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lani_paths import data_dir, ensure_backups_dir, force_utf8_io  # noqa: E402

force_utf8_io()


def main():
    try:
        json.load(sys.stdin)
    except json.JSONDecodeError:
        pass

    backup_dir = ensure_backups_dir() / datetime.now().strftime("%Y%m%d")
    backup_dir.mkdir(parents=True, exist_ok=True)

    data = data_dir()
    if data.exists():
        backed_up = []
        # The home language's databases, and every other language's (languages/<code>/*.json).
        for json_file in [*data.glob("*.json"), *data.glob("languages/*/*.json")]:
            rel = json_file.relative_to(data)
            try:
                (backup_dir / rel).parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(json_file, backup_dir / rel)
                backed_up.append(str(rel))
            except Exception as e:
                print(f"[Lani] Warning: Could not backup {json_file}: {e}", file=sys.stderr)

        if backed_up:
            print(f"[Lani] 📦 Session backup created: {backup_dir}/")
            print(f"[Lani] 💾 Files backed up: {', '.join(backed_up)}")

    profile_path = data / "learner-profile.json"
    if profile_path.exists():
        try:
            with open(profile_path, 'r') as f:
                profile = json.load(f)

            streak = profile.get("current_streak_days", 0)
            total_sessions = profile.get("total_sessions", 0)

            print(f"[Lani] 🔥 Current streak: {streak} days")
            print(f"[Lani] 📊 Total sessions: {total_sessions}")
            print(f"[Lani] 👋 Great work today!")

        except Exception as e:
            print(f"[Lani] Could not read stats: {e}", file=sys.stderr)

    sys.exit(0)


if __name__ == "__main__":
    main()
