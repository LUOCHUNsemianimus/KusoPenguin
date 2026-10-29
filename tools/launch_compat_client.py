#!/usr/bin/env python3
"""Launch an isolated production-compatible NeoForge 1.21.1 client.

The dev ModDevGradle launcher hits nativeerr54 with YSM 2.6.5 on this toolchain. This
script starts the real NeoForge 1.21.1 profile in a private game directory instead,
using only the built mod jar and the pinned TLM/YSM jars. It never reads account or
authentication files, never uses the network, and never edits the user's profile,
mods or saves. Local machine paths come from an ignored JSON config (dev-libs/).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import uuid
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = REPO / "dev-libs" / "compat-launch.json"
DEFAULT_YSM = REPO / "dev-libs" / "ysm-2.6.5-neoforge+mc1.21.1-release.jar"
MELODIES_DESTINATION = "immersive_melodies-neoforge-0.8.0+1.21.1.jar"
CONFIG_KEYS = ("profileJson", "librariesDir", "assetsDir", "javaExe")


def fail(message: str) -> None:
    print(f"compat launch: {message}", file=sys.stderr)
    raise SystemExit(2)


def resolve_cli(value: str) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if not path.is_absolute() else path


def load_config(config_path: Path) -> dict[str, Path]:
    if not config_path.is_file():
        fail(
            f"local config not found: {config_path}. Create an ignored dev-libs/compat-launch.json "
            f"with keys {', '.join(CONFIG_KEYS)}; see docs/BREWING_REVIVAL.md."
        )
    try:
        raw = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read local config {config_path}: {exc}")
    if not isinstance(raw, dict):
        fail(f"local config {config_path} must be a JSON object")
    missing = [key for key in CONFIG_KEYS if not str(raw.get(key, "")).strip()]
    if missing:
        fail(f"local config {config_path} is missing non-empty keys: {', '.join(missing)}")
    base = config_path.parent
    resolved = {}
    for key in CONFIG_KEYS:
        value = Path(str(raw[key])).expanduser()
        resolved[key] = value if value.is_absolute() else (base / value).resolve()
    return resolved


def allow(rules) -> bool:
    """Evaluate Windows rules; every optional feature request resolves to false."""
    if not rules:
        return True
    allowed = False
    for rule in rules:
        os_rule = rule.get("os", {})
        if os_rule.get("name") and os_rule.get("name") != "windows":
            continue
        if os_rule.get("arch") and os_rule.get("arch") not in ("amd64", "x86_64"):
            continue
        features = rule.get("features")
        # False features match this launcher; true requirements cannot be satisfied.
        if features is True or (isinstance(features, dict) and any(features.values())):
            continue
        allowed = rule.get("action") == "allow"
    return allowed


def expand_arguments(items, values: dict[str, str]) -> list[str]:
    def expand(text: str) -> str:
        for key, value in values.items():
            text = text.replace("${" + key + "}", str(value))
        if "${" in text:
            fail(f"unresolved profile placeholder in argument: {text}")
        return text

    out: list[str] = []
    for item in items:
        if isinstance(item, str):
            out.append(expand(item))
        elif isinstance(item, dict) and allow(item.get("rules")):
            value = item.get("value", [])
            out.extend(expand(part) for part in (value if isinstance(value, list) else [value]))
    return out


def latest_built_jar() -> Path | None:
    jars = [
        path for path in (REPO / "build" / "libs").glob("*.jar")
        if not any(tag in path.name for tag in ("-sources", "-javadoc", "-plain"))
    ]
    return max(jars, key=lambda path: path.stat().st_mtime) if jars else None


def main() -> int:
    parser = argparse.ArgumentParser(description="Isolated production-compatible NeoForge client")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG), help="ignored local JSON config")
    parser.add_argument("--game-dir", default=str(REPO / "run" / "compat-client"),
                        help="isolated compat game directory")
    parser.add_argument("--mod-jar", help="current built kuso_penguin jar")
    parser.add_argument("--maid-jar", required=True, help="resolved TLM 1.5.3 runtime jar")
    parser.add_argument("--ysm-jar", default=str(DEFAULT_YSM), help="pinned local YSM 2.6.5 jar")
    parser.add_argument("--melodies-jar",
                        help="optional resolved Immersive Melodies jar; omitted means absent-mod run")
    args = parser.parse_args()

    config_path = resolve_cli(args.config)
    config = load_config(config_path)
    profile_path = config["profileJson"]
    libraries_dir = config["librariesDir"]
    assets_dir = config["assetsDir"]
    java_exe = config["javaExe"]
    game_dir = resolve_cli(args.game_dir)
    maid_jar = resolve_cli(args.maid_jar)
    ysm_jar = resolve_cli(args.ysm_jar)
    melodies_jar = resolve_cli(args.melodies_jar) if args.melodies_jar else None
    mod_jar = resolve_cli(args.mod_jar) if args.mod_jar else latest_built_jar()

    for label, path in (("profile", profile_path), ("mod jar", mod_jar), ("maid jar", maid_jar),
                        ("YSM jar", ysm_jar), ("java", java_exe)):
        if path is None or not path.is_file():
            fail(f"{label} not found: {path}")
    if melodies_jar is not None and not melodies_jar.is_file():
        fail(f"Immersive Melodies jar not found: {melodies_jar}")
    for label, path in (("libraries", libraries_dir), ("assets", assets_dir)):
        if not path.is_dir():
            fail(f"{label} directory not found: {path}")

    try:
        profile = json.loads(profile_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read version profile {profile_path}: {exc}")
    if not isinstance(profile, dict):
        fail(f"version profile must be a JSON object: {profile_path}")
    profile_id = str(profile.get("id", ""))
    libraries = profile.get("libraries")
    arguments = profile.get("arguments")
    main_class = str(profile.get("mainClass") or "")
    if not isinstance(libraries, list) or not isinstance(arguments, dict):
        fail(f"unsupported profile (inherited/minimal profile without libraries/arguments): {profile_path}")

    game_args: list[str] = []
    for item in arguments.get("game", []):
        if isinstance(item, str):
            game_args.append(item)
        elif isinstance(item, dict) and allow(item.get("rules")):
            value = item.get("value", [])
            game_args.extend(str(part) for part in (value if isinstance(value, list) else [value]))

    def flag(name: str) -> str:
        # A trailing flag has no value; return empty so validation reports it instead of raising.
        index = game_args.index(name) if name in game_args else -1
        return game_args[index + 1] if 0 <= index < len(game_args) - 1 else ""

    # Validate structure, not a user-editable profile id/label or a guessed library name.
    neo_version = flag("--fml.neoForgeVersion")
    parts = neo_version.split(".")
    version_ok = (len(parts) >= 2 and parts[0] == "21" and parts[1] == "1"
                  and all(part.isdigit() for part in parts))
    problems: list[str] = []
    if main_class.rsplit(".", 1)[-1] != "BootstrapLauncher":
        problems.append(f"mainClass is not BootstrapLauncher: {main_class!r}")
    if flag("--launchTarget") != "forgeclient":
        problems.append("missing literal game argument --launchTarget forgeclient")
    if flag("--fml.mcVersion") != "1.21.1":
        problems.append("missing literal game argument --fml.mcVersion 1.21.1")
    if not version_ok:
        problems.append(f"--fml.neoForgeVersion is not 21.1.x: {neo_version!r}")
    names = [str(lib.get("name", "")) for lib in libraries if isinstance(lib, dict)]
    if not any(name.startswith("net.neoforged.fancymodloader:loader:") for name in names):
        problems.append("missing library net.neoforged.fancymodloader:loader")
    if version_ok:
        neo_dir = libraries_dir / "net" / "neoforged" / "neoforge" / neo_version
        required = (neo_dir / f"neoforge-{neo_version}-universal.jar",
                    neo_dir / f"neoforge-{neo_version}-client.jar")
        absent = [str(path) for path in required if not path.is_file()]
        if absent:
            problems.append("missing NeoForge universal/client jar(s): " + ", ".join(absent))
    if problems:
        fail("profile is not a supported NeoForge 1.21.1 client:\n  " + "\n  ".join(problems))

    classpath: list[str] = []
    missing: list[str] = []
    native_archives: list[Path] = []
    for lib in libraries:
        if not allow(lib.get("rules")):
            continue
        artifact = lib.get("downloads", {}).get("artifact", {})
        relative = artifact.get("path")
        if not relative:
            continue
        path = libraries_dir / relative
        if not path.is_file():
            missing.append(str(path))
            continue
        classpath.append(str(path))
        if "natives-windows" in path.name.lower():
            native_archives.append(path)
    version_jar = profile_path.with_suffix(".jar")
    if not version_jar.is_file():
        missing.append(str(version_jar))
    if missing:
        fail("missing required libraries/profile jar:\n  " + "\n  ".join(missing))

    natives_dir = game_dir / "natives"
    mods_dir = game_dir / "mods"
    natives_dir.mkdir(parents=True, exist_ok=True)
    mods_dir.mkdir(parents=True, exist_ok=True)
    for archive in native_archives:
        try:
            with zipfile.ZipFile(archive) as jar:
                for name in jar.namelist():
                    if name.lower().endswith(".dll"):
                        (natives_dir / Path(name).name).write_bytes(jar.read(name))
        except (OSError, zipfile.BadZipFile) as exc:
            fail(f"cannot extract native DLLs from {archive}: {exc}")

    for source in (mod_jar, maid_jar, ysm_jar):
        destination = mods_dir / source.name
        if destination.resolve() != source.resolve():
            shutil.copy2(source, destination)
    if melodies_jar is not None:
        # Always stage under the official filename so the Maven artifact name cannot create a
        # duplicate mod next to a manually downloaded 0.8.0 jar.
        destination = mods_dir / MELODIES_DESTINATION
        if destination.resolve() != melodies_jar.resolve():
            shutil.copy2(melodies_jar, destination)

    offline_uuid = str(uuid.UUID(bytes=hashlib.md5(b"OfflinePlayer:Dev").digest(), version=3))
    values = {
        "auth_player_name": "Dev",
        "version_name": profile_id,
        "game_directory": str(game_dir),
        "assets_root": str(assets_dir),
        "assets_index_name": str(profile.get("assetIndex", {}).get("id", "1.21")),
        "auth_uuid": offline_uuid,
        "auth_access_token": "0",
        "clientid": "0",
        "auth_xuid": "0",
        "user_type": "legacy",
        "version_type": "release",
        "natives_directory": str(natives_dir),
        "launcher_name": "kuso-penguin-compat",
        "launcher_version": "1",
        "classpath": os.pathsep.join(classpath + [str(version_jar)]),
        "primary_jar_name": version_jar.name,
        "library_directory": str(libraries_dir),
        "classpath_separator": os.pathsep,
    }
    command = [str(java_exe), "-Xmx3G"]
    command += expand_arguments(arguments.get("jvm", []), values)
    command.append(str(main_class))
    command += expand_arguments(arguments.get("game", []), values)

    print(f"compat launch: {profile_id} (game dir {game_dir})", flush=True)
    creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0
    try:
        return subprocess.run(command, cwd=str(game_dir), creationflags=creationflags).returncode
    except OSError as exc:
        fail(f"cannot start java: {exc}")


if __name__ == "__main__":
    raise SystemExit(main())
