#!/usr/bin/env python3
"""
apply_branding.py

Reapplies Replay's branding overrides on top of a freshly-merged upstream
mpvRx tree. Safe to run repeatedly (idempotent) and safe to run right after
`git merge upstream/master` even if upstream added/changed unrelated strings.

Usage:
    python3 scripts/apply_branding.py            # apply to ./main/res
    python3 scripts/apply_branding.py --check     # dry-run, just report mismatches

Add this as a required step in CONTRIBUTING-PLAYBOOK.md workflow, or wire it
into a `post-merge` git hook (see setup_hook() below) so it runs automatically
every time upstream is pulled in.
"""

import argparse
import json
import re
import sys
from pathlib import Path

def _find_res_root() -> Path:
    """
    Auto-detect the res/ directory instead of assuming a fixed folder name
    (the app module has been zipped as both `main/` and `src/` at different
    times) - walk up from this script and look for */res/values/strings.xml.
    """
    here = Path(__file__).resolve().parent
    for candidate in [here.parent, *here.parents]:
        for res_dir in candidate.glob("*/res"):
            if (res_dir / "values" / "strings.xml").exists():
                return res_dir
        direct = candidate / "res"
        if (direct / "values" / "strings.xml").exists():
            return direct
    raise FileNotFoundError(
        "Could not locate res/values/strings.xml anywhere near this script. "
        "Run apply_branding.py from within the app module (or pass --res-root)."
    )


RES_ROOT = _find_res_root()

# ---------------------------------------------------------------------------
# Each entry: (locale_dir, key, old_upstream_value_or_None, new_replay_value)
#
# old_value may be None to mean "replace whatever is currently there" (used
# sparingly - prefer exact old_value matches so the script can warn instead
# of silently overwriting something unexpected upstream changed).
# ---------------------------------------------------------------------------

BRANDING = {
    "values": [
        ("app_name", "mpvRx", "Replay"),
        ("pref_appearance_system_font_summary",
         "Use the Android system typeface across mpvRx instead of Google Sans Flex",
         "Use the Android system typeface across Replay instead of Google Sans Flex"),
        ("theme_cotton_candy", "Cotton Candy", "Candy"),
        ("github_repo_url", "https://github.com/Riteshp2001/mpvRx", "https://github.com/Utsavrajputt/Replay"),
        ("ui_mpvrx_needs_access_to_your_video_files_to_provide_its_core_funct",
         "mpvRx needs access to your video files to provide its core functionality as a media player.",
         "Replay needs access to your video files to provide its core functionality as a media player."),
        ("ui_mpvrx_has_always_required_storage_access_permission_as_it_s_esse",
         "mpvRx has always required storage access permission as it\\'s essential for the app to find all media and subtitle files on your device, including the ones that are not supported by the system.",
         "Replay has always required storage access permission as it\\'s essential for the app to find all media and subtitle files on your device, including the ones that are not supported by the system."),
        ("ui_mpvrx_is_an_open_source_project_you_can_review_the_source_code_a",
         "mpvRx is an open source project. You can review the source code and verify how permissions are used by visiting our GitHub repository at:",
         "Replay is an open source project. You can review the source code and verify how permissions are used by visiting our GitHub repository at:"),
        ("ui_mpv_documentation", "mpv Documentation", "Replay Documentation"),
        ("ui_by_ritesh_pandit", "By Ritesh Pandit", "By Utsav Rajput"),
        ("ui_if_you_enjoy_mpvrx_consider_supporting_its_development_every_bit",
         "If you enjoy mpvRx, consider supporting its development. Every bit helps!",
         "Enjoying Replay? Consider supporting the original developer who made it possible. Every contribution makes a difference!"),
        ("ui_core_open_source_dependencies_used_by_mpvrx",
         "Core open source dependencies used by mpvRx.",
         "Core open source dependencies used by Replay."),
        ("pref_codecs_banner_desc",
         "Hardware decoders decode video directly on dedicated VPU/GPU chips, using minimal power. Software decoders run on CPU cores, causing higher battery drain and heating during high-bitrate or 4K playback (e.g. AV1 without HW support).",
         "Hardware decoders (\u26a1) decode video directly on dedicated VPU/GPU chips, using minimal power. Software decoders (\U0001f4bb) run on CPU cores, causing higher battery drain and heating during high-bitrate or 4K playback (e.g. AV1 without HW support)."),
        ("pref_codecs_filter_hardware", "Hardware (%d)", "Hardware \u26a1 (%d)"),
        ("pref_codecs_filter_software", "Software (%d)", "Software \U0001f4bb (%d)"),
        ("pref_codecs_badge_hardware", "Hardware", "\u26a1 Hardware"),
        ("pref_codecs_badge_software", "Software", "\U0001f4bb Software"),
        ("pref_codecs_badge_hw_short", "HW", "\u26a1 HW"),
        ("pref_codecs_badge_sw_short", "SW", "\U0001f4bb SW"),
    ],
    # Locale entries follow the same (key, old, new) shape. Add/maintain per
    # language here; see README note at bottom for how to add a new locale.
}

# NOTE: keeping this table to English only here to stay readable. The full
# 9-locale translated table lives alongside this script in
# branding_locales.json (auto-loaded below).
_locales_path = Path(__file__).resolve().parent / "branding_locales.json"
if _locales_path.exists():
    with open(_locales_path, encoding="utf-8") as f:
        locale_data = json.load(f)
    for locale_dir, entries in locale_data.items():
        BRANDING[locale_dir] = [tuple(e) for e in entries]


def apply_to_file(path: Path, entries, check_only: bool) -> tuple[int, int, list[str]]:
    """Returns (applied_count, already_correct_count, warnings)."""
    if not path.exists():
        return 0, 0, [f"MISSING FILE: {path}"]

    text = path.read_text(encoding="utf-8")
    applied = 0
    already_ok = 0
    warnings = []

    for key, old_val, new_val in entries:
        pattern_new = re.compile(r'(<string name="' + re.escape(key) + r'"[^>]*>)' + re.escape(new_val) + r'(</string>)')
        if pattern_new.search(text):
            already_ok += 1
            continue

        pattern_old = re.compile(r'(<string name="' + re.escape(key) + r'"[^>]*>)' + re.escape(old_val) + r'(</string>)')
        new_text, n = pattern_old.subn(lambda m: m.group(1) + new_val + m.group(2), text)
        if n == 1:
            if not check_only:
                text = new_text
            applied += 1
        elif n == 0:
            # Neither old nor new value found as expected -> upstream likely
            # changed the string again. Flag it instead of guessing.
            exists = re.search(r'<string name="' + re.escape(key) + r'"[^>]*>(.*?)</string>', text, re.DOTALL)
            if exists:
                warnings.append(
                    f"{path.name}: '{key}' has unexpected value, needs manual review:\n"
                    f"    current: {exists.group(1)[:120]}"
                )
            else:
                warnings.append(f"{path.name}: '{key}' not found at all (upstream may have removed it)")
        else:
            warnings.append(f"{path.name}: '{key}' matched {n} times, expected 1 (duplicate resource?)")

    if not check_only and applied:
        path.write_text(text, encoding="utf-8")

    return applied, already_ok, warnings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="dry run, report only")
    parser.add_argument("--res-root", type=str, default=None, help="override auto-detected res/ path")
    args = parser.parse_args()

    global RES_ROOT
    if args.res_root:
        RES_ROOT = Path(args.res_root)

    total_applied = 0
    total_ok = 0
    all_warnings = []

    for locale_dir, entries in BRANDING.items():
        path = RES_ROOT / locale_dir / "strings.xml"
        applied, ok, warnings = apply_to_file(path, entries, args.check)
        total_applied += applied
        total_ok += ok
        all_warnings.extend(warnings)
        if applied or warnings:
            verb = "would fix" if args.check else "fixed"
            print(f"{locale_dir}: {verb} {applied}, already correct {ok}")

    print(f"\n{'DRY RUN: ' if args.check else ''}Total: {total_applied} applied, {total_ok} already correct")

    if all_warnings:
        print(f"\n{len(all_warnings)} item(s) need manual review:")
        for w in all_warnings:
            print(f"  - {w}")
        sys.exit(1 if args.check else 0)

    if args.check and total_applied:
        print("\nRun without --check to apply these fixes.")
        sys.exit(1)


if __name__ == "__main__":
    main()
