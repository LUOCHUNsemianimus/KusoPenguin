#!/usr/bin/env python3
"""Generate PenguinGeometry.java from the native Blockbench Java export.

The Blockbench native export is not a compilable Minecraft 1.21.1 model class:
it targets an older API and contains a full EntityModel implementation.  This
script extracts only the balanced ``createBodyLayer`` method and wraps it in the
small helper class used by ``PenguinBodyModel``.

Blockbench also emits group names such as ``ribbon_tail_-1_0_joint`` as Java
local variable names.  Those hyphens are illegal in Java, so identifiers are
mechanically rewritten to ``..._neg1_...``; quoted ModelPart names and all
geometry/pose values are left untouched.

Usage:
    python tools/generate_penguin_geometry.py
    python tools/generate_penguin_geometry.py --check

``--check`` never writes; it exits non-zero when the checked-in generated file
differs from what the current native export would produce.  Re-run without
``--check`` to regenerate deliberately.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "models" / "penguin_body_export.java"
DEFAULT_BBMODEL = ROOT / "models" / "penguin_body.bbmodel"
DEFAULT_OUTPUT = (
    ROOT / "src" / "main" / "java" / "com" / "luochun" / "kusopenguin"
    / "client" / "PenguinGeometry.java"
)

METHOD_HEADER_RE = re.compile(
    r"public\s+static\s+LayerDefinition\s+createBodyLayer\s*\(\s*\)\s*\{"
)
ATLAS_RE = re.compile(
    r"return\s+LayerDefinition\.create\(\s*meshdefinition\s*,\s*(\d+)\s*,\s*(\d+)\s*\)\s*;"
)
IMPORTS = (
    "net.minecraft.client.model.geom.PartPose",
    "net.minecraft.client.model.geom.builders.CubeDeformation",
    "net.minecraft.client.model.geom.builders.CubeListBuilder",
    "net.minecraft.client.model.geom.builders.LayerDefinition",
    "net.minecraft.client.model.geom.builders.MeshDefinition",
    "net.minecraft.client.model.geom.builders.PartDefinition",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def extract_method(source_text: str) -> str:
    """Return the complete createBodyLayer method, braces balanced and verbatim."""
    match = METHOD_HEADER_RE.search(source_text)
    if match is None:
        raise ValueError("createBodyLayer() not found in native export")
    if METHOD_HEADER_RE.search(source_text, match.end()) is not None:
        raise ValueError("createBodyLayer() found more than once in native export")

    start = match.start()
    brace = match.end() - 1
    depth = 0
    index = brace
    length = len(source_text)
    in_line_comment = False
    in_block_comment = False
    in_string = False
    in_char = False
    escaped = False

    while index < length:
        char = source_text[index]
        following = source_text[index + 1] if index + 1 < length else ""

        if in_line_comment:
            if char == "\n":
                in_line_comment = False
        elif in_block_comment:
            if char == "*" and following == "/":
                in_block_comment = False
                index += 2
                continue
        elif in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif in_char:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "'":
                in_char = False
        else:
            if char == "/" and following == "/":
                in_line_comment = True
                index += 2
                continue
            if char == "/" and following == "*":
                in_block_comment = True
                index += 2
                continue
            if char == '"':
                in_string = True
            elif char == "'":
                in_char = True
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return source_text[start:index + 1]

        index += 1

    raise ValueError("unbalanced braces while extracting createBodyLayer()")


IDENT_START = frozenset(
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_$"
)
IDENT_PART = frozenset(
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_$"
)


def sanitize_identifiers(method: str) -> str:
    """Make BB group names such as ``ribbon_tail_-1_0_joint`` valid Java.

    Only identifier tokens in code are changed; quoted ModelPart names keep the
    original Blockbench text so lookups and the saved model still match.
    """
    output: list[str] = []
    index = 0
    length = len(method)
    in_line_comment = False
    in_block_comment = False
    in_string = False
    in_char = False
    escaped = False

    while index < length:
        char = method[index]
        following = method[index + 1] if index + 1 < length else ""

        if in_line_comment:
            output.append(char)
            if char == "\n":
                in_line_comment = False
        elif in_block_comment:
            output.append(char)
            if char == "*" and following == "/":
                output.append("/")
                in_block_comment = False
                index += 1
        elif in_string:
            output.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif in_char:
            output.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "'":
                in_char = False
        else:
            if char == "/" and following == "/":
                output.append("//")
                in_line_comment = True
                index += 1
            elif char == "/" and following == "*":
                output.append("/*")
                in_block_comment = True
                index += 1
            elif char == '"':
                output.append(char)
                in_string = True
            elif char == "'":
                output.append(char)
                in_char = True
            elif char in IDENT_START:
                end = index
                while end < length:
                    if method[end] in IDENT_PART:
                        end += 1
                    elif (
                        method[end] == "-"
                        and end + 1 < length
                        and method[end + 1].isdigit()
                    ):
                        end += 1
                    else:
                        break
                token = method[index:end]
                if "-" in token:
                    token = token.replace("-", "neg")
                output.append(token)
                index = end - 1
            else:
                output.append(char)

        index += 1

    return "".join(output)


def read_bbmodel_metadata(bbmodel_path: Path) -> tuple[int, tuple[int, int]]:
    """Read the expected cube count and texture size from the Blockbench model."""
    try:
        data = json.loads(bbmodel_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise ValueError(
            f"invalid bbmodel JSON in {bbmodel_path}: {error}"
        ) from error

    if not isinstance(data, dict):
        raise ValueError(f"bbmodel metadata in {bbmodel_path} is not an object")

    elements = data.get("elements")
    if not isinstance(elements, list):
        raise ValueError(f"bbmodel metadata in {bbmodel_path} has no elements list")
    cube_count = sum(
        1
        for element in elements
        if isinstance(element, dict) and element.get("type") == "cube"
    )
    if cube_count == 0:
        raise ValueError(f"bbmodel metadata in {bbmodel_path} has no type=cube elements")

    resolution = data.get("resolution")
    if not isinstance(resolution, dict):
        raise ValueError(
            f"bbmodel metadata in {bbmodel_path} has no resolution object"
        )
    width = resolution.get("width")
    height = resolution.get("height")
    if (
        not isinstance(width, int)
        or isinstance(width, bool)
        or not isinstance(height, int)
        or isinstance(height, bool)
        or width <= 0
        or height <= 0
    ):
        raise ValueError(
            f"bbmodel resolution in {bbmodel_path} needs positive integer "
            f"width/height, got {width!r}x{height!r}"
        )

    return cube_count, (width, height)


def render(
    source_path: Path, bbmodel_path: Path
) -> tuple[str, int, tuple[int, int], str, str]:
    source_text = source_path.read_text(encoding="utf-8")
    method = sanitize_identifiers(extract_method(source_text))
    bb_cube_count, bb_atlas = read_bbmodel_metadata(bbmodel_path)

    cube_count = method.count(".addBox(")
    if cube_count != bb_cube_count:
        raise ValueError(
            f"native createBodyLayer has {cube_count} addBox cubes but "
            f"bbmodel declares {bb_cube_count} type=cube elements"
        )

    atlas_match = ATLAS_RE.search(method)
    if atlas_match is None:
        raise ValueError("LayerDefinition.create(meshdefinition, W, H) not found")
    atlas = (int(atlas_match.group(1)), int(atlas_match.group(2)))
    if atlas != bb_atlas:
        raise ValueError(
            f"native atlas {atlas[0]}x{atlas[1]} but bbmodel resolution is "
            f"{bb_atlas[0]}x{bb_atlas[1]}"
        )

    source_hash = sha256(source_path)
    bbmodel_hash = sha256(bbmodel_path)

    lines = [
        "package com.luochun.kusopenguin.client;",
        "",
    ]
    lines.extend(f"import {name};" for name in IMPORTS)
    lines.extend(
        [
            "",
            "/** Generated from the native Blockbench Java export; do not edit geometry by hand. */",
            "public final class PenguinGeometry {",
            "    private PenguinGeometry() {}",
            "",
            "    " + method,
            "}",
            "",
        ]
    )
    return "\n".join(lines), cube_count, atlas, source_hash, bbmodel_hash


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="compare the current generated file instead of writing it",
    )
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--bbmodel", type=Path, default=DEFAULT_BBMODEL)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args(argv)

    try:
        expected, cube_count, atlas, source_hash, bbmodel_hash = render(
            args.source, args.bbmodel
        )
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    expected_bytes = expected.encode("utf-8")
    audit = (
        f"{cube_count} cubes, atlas {atlas[0]}x{atlas[1]}; "
        f"export sha256 {source_hash}; bbmodel sha256 {bbmodel_hash}"
    )

    if args.check:
        if not args.output.exists():
            print(f"MISMATCH: generated file is missing: {args.output}", file=sys.stderr)
            return 1
        current_bytes = args.output.read_bytes()
        # A trailing-newline style difference is not geometry drift.
        if current_bytes.rstrip(b"\r\n") != expected_bytes.rstrip(b"\r\n"):
            print(
                f"MISMATCH: {args.output} does not match the native export; "
                "run without --check to regenerate",
                file=sys.stderr,
            )
            return 1
        print(f"OK: {args.output} matches the native export ({audit})")
        return 0

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(expected_bytes)
    print(f"wrote {args.output} ({audit})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
