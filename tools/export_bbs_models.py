"""Convert captured Cataclysm/Penguin native geometry into editable free .bbmodel files.

This tool is intentionally local-only and deterministic. It reads the captured
``native_scene.json`` plus the already-composited PNG textures, and writes one
free-model `.bbmodel` (with one embedded PNG) per requested model.

It never writes runtime assets, never touches game Java, and never downloads
anything. Only Python's standard library and Pillow are used.

Coordinate contract (see docs/BBS_EXPORT.md):
  * native matrices are 16-float column-major, Y-down, model pixels, floor 24
  * model matrices are row-major 4x4 internally
  * C = diag(-1,-1,1,1), G = translate(0,24,0)
  * boss H = outer_scale_matrix * G * C
  * penguin authoring H = G * C
  * penguin game H = G * C * translate(0,12,0) * scale(0.5)
  * rigid F = rigid(H * nodeWorld * C)
  * mesh local vertex = F.R^T * (worldPoint - F.T)
"""

from __future__ import annotations

import argparse
import base64
import io
import json
import math
import sys
import traceback
import uuid
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = ROOT / "dev-libs" / "bbs-export-input" / "native_scene.json"
DEFAULT_TEXTURES = ROOT / "dev-libs" / "bbs-export-input" / "textures"
DEFAULT_OUT = ROOT / "dev-libs" / "bbs-export-output"

BOSS_ORDER = ("scylla", "maledictus", "ignis")
PALETTE_NAME = "penguin.png"
SOUL_NAME = "ignis_soul.png"
TEXTURE_PAD = 4
RECONSTRUCTION_TOLERANCE = 2e-4
UUID_NS = uuid.uuid5(uuid.NAMESPACE_URL, "https://kuso-penguin.invalid/bbs-export/20261029")


# ---------------------------------------------------------------------------
# Small matrix / vector helpers
# ---------------------------------------------------------------------------


def mat_identity():
    return [
        [1.0, 0.0, 0.0, 0.0],
        [0.0, 1.0, 0.0, 0.0],
        [0.0, 0.0, 1.0, 0.0],
        [0.0, 0.0, 0.0, 1.0],
    ]


def mat_diag(x, y, z):
    return [
        [float(x), 0.0, 0.0, 0.0],
        [0.0, float(y), 0.0, 0.0],
        [0.0, 0.0, float(z), 0.0],
        [0.0, 0.0, 0.0, 1.0],
    ]


def mat_translate(x, y, z):
    m = mat_identity()
    m[0][3] = float(x)
    m[1][3] = float(y)
    m[2][3] = float(z)
    return m


def mat_scale(s):
    return mat_diag(s, s, s)


def mat_mul(a, b):
    return [
        [sum(a[r][k] * b[k][c] for k in range(4)) for c in range(4)]
        for r in range(4)
    ]


def mat_from_column_major(values, label):
    if not isinstance(values, list) or len(values) != 16:
        raise ValueError(f"{label}: expected 16 column-major floats, got {type(values).__name__}")
    try:
        vals = [float(v) for v in values]
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{label}: matrix contains a non-numeric value") from exc
    if not all(math.isfinite(v) for v in vals):
        raise ValueError(f"{label}: matrix contains a non-finite value")
    return [[vals[c * 4 + r] for c in range(4)] for r in range(4)]


def mat_point(m, p):
    x, y, z = float(p[0]), float(p[1]), float(p[2])
    return [
        m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
        m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
        m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3],
    ]


def vec_add(a, b):
    return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]


def vec_sub(a, b):
    return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]


def vec_dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def vec_cross(a, b):
    return [
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    ]


def vec_norm(a):
    return math.sqrt(vec_dot(a, a))


def vec_normalize(a, label):
    n = vec_norm(a)
    if n < 1e-12:
        raise ValueError(f"{label}: zero-length basis vector, cannot build a rigid frame")
    return [a[0] / n, a[1] / n, a[2] / n]


def mat3_mul(a, b):
    return [[sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3)] for r in range(3)]


def mat3_transpose(a):
    return [[a[c][r] for c in range(3)] for r in range(3)]


def mat3_vec(a, v):
    return [
        a[0][0] * v[0] + a[0][1] * v[1] + a[0][2] * v[2],
        a[1][0] * v[0] + a[1][1] * v[1] + a[1][2] * v[2],
        a[2][0] * v[0] + a[2][1] * v[1] + a[2][2] * v[2],
    ]


def mat3_det(a):
    return (
        a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1])
        - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
        + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0])
    )


def rigid_from_matrix(m, label):
    """Return (R, T) with orthonormal right-handed R, positive determinant."""
    t = [m[0][3], m[1][3], m[2][3]]
    col_x = [m[0][0], m[1][0], m[2][0]]
    col_y = [m[0][1], m[1][1], m[2][1]]
    x = vec_normalize(col_x, label + " column X")
    z = vec_normalize(vec_cross(x, col_y), label + " column X x column Y")
    y = vec_cross(z, x)
    y = vec_normalize(y, label + " reconstructed Y")
    # Columns are X, Y, Z. The construction above is right-handed by definition.
    r = [
        [x[0], y[0], z[0]],
        [x[1], y[1], z[1]],
        [x[2], y[2], z[2]],
    ]
    det = mat3_det(r)
    if not math.isfinite(det) or det <= 1e-9:
        raise ValueError(f"{label}: rigid frame has non-positive determinant ({det})")
    if not all(math.isfinite(v) for v in t):
        raise ValueError(f"{label}: rigid frame translation is non-finite")
    return r, t


# ---------------------------------------------------------------------------
# ZYX Euler conversion (R = Rz * Ry * Rx)
# ---------------------------------------------------------------------------


def mat3_from_euler_deg(rotation):
    x, y, z = (math.radians(float(v)) for v in rotation)
    cx, sx = math.cos(x), math.sin(x)
    cy, sy = math.cos(y), math.sin(y)
    cz, sz = math.cos(z), math.sin(z)
    return [
        [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
        [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
        [-sy, cy * sx, cy * cx],
    ]


def euler_deg_from_mat3(r, label):
    cy = math.sqrt(r[2][1] * r[2][1] + r[2][2] * r[2][2])
    if cy <= 1e-8:
        x = math.atan2(-r[1][2], r[1][1])
        y = math.atan2(-r[2][0], cy)
        z = 0.0
    else:
        x = math.atan2(r[2][1], r[2][2])
        y = math.atan2(-r[2][0], cy)
        z = math.atan2(r[1][0], r[0][0])
    rotation = [math.degrees(x), math.degrees(y), math.degrees(z)]
    rebuilt = mat3_from_euler_deg(rotation)
    error = max(abs(rebuilt[i][j] - r[i][j]) for i in range(3) for j in range(3))
    if error > 1e-7:
        raise ValueError(f"{label}: Euler round-trip failed (max error {error:g})")
    return rotation


# ---------------------------------------------------------------------------
# Texture / atlas helpers
# ---------------------------------------------------------------------------


def load_rgba(path):
    if not path.is_file():
        raise FileNotFoundError(f"texture not found: {path}")
    try:
        with Image.open(path) as image:
            return image.convert("RGBA")
    except OSError as exc:
        raise ValueError(f"cannot read texture {path}: {exc}") from exc


def png_bytes(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=False, compress_level=9)
    return buffer.getvalue()


def next_power_of_two(value):
    if value <= 1:
        return 1
    return 1 << (value - 1).bit_length()


def edge_padded(image, pad):
    """Replicate source edge pixels into a pad-wide border."""
    if pad <= 0:
        return image.copy()
    width, height = image.size
    padded = Image.new("RGBA", (width + 2 * pad, height + 2 * pad), (0, 0, 0, 0))
    padded.paste(image, (pad, pad))
    nearest = getattr(Image, "Resampling", Image).NEAREST

    top = image.crop((0, 0, width, 1)).resize((width, pad), nearest)
    bottom = image.crop((0, height - 1, width, height)).resize((width, pad), nearest)
    left = image.crop((0, 0, 1, height)).resize((pad, height), nearest)
    right = image.crop((width - 1, 0, width, height)).resize((pad, height), nearest)
    padded.paste(top, (pad, 0))
    padded.paste(bottom, (pad, pad + height))
    padded.paste(left, (0, pad))
    padded.paste(right, (pad + width, pad))

    corners = (
        ((0, 0, 1, 1), (0, 0)),
        ((width - 1, 0, width, 1), (pad + width, 0)),
        ((0, height - 1, 1, height), (0, pad + height)),
        ((width - 1, height - 1, width, height), (pad + width, pad + height)),
    )
    for box, position in corners:
        padded.paste(image.crop(box).resize((pad, pad), nearest), position)
    return padded


class Texture:
    """A single PNG plus the tile offsets needed to map native/palette UVs."""

    def __init__(self, image, width, height, base_tile, base_size, palette_tile=None, palette_size=None):
        self.image = image
        self.width = width
        self.height = height
        self.base_tile = base_tile
        self.base_size = base_size
        self.palette_tile = palette_tile
        self.palette_size = palette_size

    def base_uv(self, u, v):
        return [
            self.base_tile[0] + float(u) * self.base_size[0],
            self.base_tile[1] + float(v) * self.base_size[1],
        ]

    def palette_uv(self, u, v):
        if self.palette_tile is None or self.palette_size is None:
            raise ValueError("texture has no palette tile")
        return [
            self.palette_tile[0] + float(u) * self.palette_size[0],
            self.palette_tile[1] + float(v) * self.palette_size[1],
        ]


def make_base_texture(image):
    return Texture(image, image.width, image.height, (0, 0), image.size)


def make_palette_texture(image):
    return Texture(
        image,
        image.width,
        image.height,
        (0, 0),
        image.size,
        (0, 0),
        image.size,
    )


def make_atlas_texture(base, palette):
    pad = TEXTURE_PAD
    base_padded = edge_padded(base, pad)
    palette_padded = edge_padded(palette, pad)
    content_width = base.width + palette.width + 4 * pad
    content_height = max(base.height, palette.height) + 2 * pad
    width = next_power_of_two(content_width)
    height = next_power_of_two(content_height)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    canvas.paste(base_padded, (0, 0))
    canvas.paste(palette_padded, (base.width + 2 * pad, 0))
    base_tile = (pad, pad)
    palette_tile = (base.width + 3 * pad, pad)
    if palette_tile[0] + palette.width > width or base_tile[1] + base.height > height:
        raise ValueError("internal atlas packing error")
    return Texture(
        canvas,
        width,
        height,
        base_tile,
        base.size,
        palette_tile,
        palette.size,
    )


# ---------------------------------------------------------------------------
# Builder / groups / meshes
# ---------------------------------------------------------------------------


def sanitize_name(value):
    text = "".join(ch if (ch.isalnum() or ch in "_-. ") else "_" for ch in str(value))
    text = text.strip()
    return text or "group"


class Group:
    def __init__(self, builder, key, name, abs_r, abs_t, parent):
        self.key = key
        self.name = name
        self.parent = parent
        self.uuid = builder.make_uuid("group", key)
        self.abs_r = abs_r
        self.abs_t = abs_t
        self.children = []

        if parent is None:
            self.local_r = abs_r
            self.origin = list(abs_t)
        else:
            parent_rt = mat3_transpose(parent.abs_r)
            self.local_r = mat3_mul(parent_rt, abs_r)
            local_t = mat3_vec(parent_rt, vec_sub(abs_t, parent.abs_t))
            # The serialized BB pivot lives in the parent's unrotated frame, so
            # it need not equal the actual world pivot once an ancestor rotates.
            self.origin = vec_add(parent.origin, local_t)
            reconstructed_t = vec_add(
                parent.abs_t,
                mat3_vec(parent.abs_r, vec_sub(self.origin, parent.origin)),
            )
            if vec_norm(vec_sub(reconstructed_t, abs_t)) > 1e-7:
                raise ValueError(
                    f"{key}: serialized group origin does not reconstruct the world pivot"
                )
        self.rotation = euler_deg_from_mat3(self.local_r, key)
        builder.groups.append(self)

    def add(self, child):
        self.children.append(child)


class Builder:
    def __init__(self, model_id, texture):
        self.model_id = model_id
        self.texture = texture
        self.groups = []
        self.elements = []
        self.element_groups = {}
        self.records = []
        self.used_uuids = set()
        self.used_group_names = set()
        self.used_element_names = set()
        self.triangle_count = 0
        self.mesh_count = 0
        self.native_bone_count = 0
        self.penguin_joint_count = 0

    def make_uuid(self, kind, key):
        value = str(uuid.uuid5(UUID_NS, f"{self.model_id}/{kind}/{key}"))
        if value in self.used_uuids:
            raise ValueError(f"{self.model_id}: UUID collision for {kind}/{key}")
        self.used_uuids.add(value)
        return value

    def unique_group_name(self, base):
        name = sanitize_name(base)
        candidate = name
        index = 2
        while candidate in self.used_group_names:
            candidate = f"{name}_{index}"
            index += 1
        self.used_group_names.add(candidate)
        return candidate

    def unique_element_name(self, base):
        name = sanitize_name(base)
        candidate = name
        index = 2
        while candidate in self.used_element_names:
            candidate = f"{name}_{index}"
            index += 1
        self.used_element_names.add(candidate)
        return candidate

    def add_group(self, key, name, abs_r, abs_t, parent):
        group = Group(self, key, name, abs_r, abs_t, parent)
        if parent is not None:
            parent.add(group)
        return group

    def add_triangles(self, group, element_key, triangles, name_hint):
        """triangles: iterable of [(world_xyz, uv_xy), (world, uv), (world, uv)]."""
        if not triangles:
            return None
        local_by_key = {}
        vertices_json = {}
        faces_json = {}
        first_world = {}
        row_template = []

        for triangle in triangles:
            if len(triangle) != 3:
                raise ValueError(f"{element_key}: triangle with {len(triangle)} vertices")
            corner_ids = []
            for world, uv in triangle:
                if len(world) != 3 or len(uv) != 2:
                    raise ValueError(f"{element_key}: malformed vertex")
                if not all(math.isfinite(float(value)) for value in world):
                    raise ValueError(f"{element_key}: non-finite world position")
                if not all(math.isfinite(float(value)) for value in uv):
                    raise ValueError(f"{element_key}: non-finite UV")
                u = float(uv[0])
                v = float(uv[1])
                if u < -1e-9 or v < -1e-9 or u > self.texture.width + 1e-9 or v > self.texture.height + 1e-9:
                    raise ValueError(
                        f"{element_key}: UV ({u:g}, {v:g}) outside texture "
                        f"{self.texture.width}x{self.texture.height}"
                    )
                u = min(max(u, 0.0), float(self.texture.width))
                v = min(max(v, 0.0), float(self.texture.height))
                local = mat3_vec(
                    mat3_transpose(group.abs_r),
                    vec_sub([float(world[0]), float(world[1]), float(world[2])], group.abs_t),
                )
                cache_key = (local[0], local[1], local[2], u, v)
                vertex_id = local_by_key.get(cache_key)
                if vertex_id is None:
                    vertex_id = f"v{len(local_by_key)}"
                    local_by_key[cache_key] = vertex_id
                    vertices_json[vertex_id] = [local[0], local[1], local[2]]
                    first_world[vertex_id] = [float(world[0]), float(world[1]), float(world[2])]
                corner_ids.append((vertex_id, u, v))
            face_id = f"f{len(faces_json)}"
            faces_json[face_id] = {
                "vertices": [corner[0] for corner in corner_ids],
                "uv": {corner[0]: [corner[1], corner[2]] for corner in corner_ids},
                "texture": 0,
            }
            row_template.append(face_id)

        if not faces_json:
            return None

        element_uuid = self.make_uuid("element", element_key)
        element = {
            "name": self.unique_element_name(name_hint),
            "type": "mesh",
            "uuid": element_uuid,
            "origin": list(group.origin),
            "rotation": [0.0, 0.0, 0.0],
            "vertices": vertices_json,
            "faces": faces_json,
            "texture": 0,
            "visibility": True,
            "export": True,
            "shading": "flat",
        }
        self.elements.append(element)
        self.element_groups[element_uuid] = group
        self.mesh_count += 1
        self.triangle_count += len(faces_json)
        group.add(element_uuid)
        for vertex_id, world in first_world.items():
            self.records.append((element_uuid, vertex_id, world))
        return element


# ---------------------------------------------------------------------------
# Scene validation / model construction
# ---------------------------------------------------------------------------


def triangle_area(a, b, c):
    cross = vec_cross(vec_sub(b, a), vec_sub(c, a))
    return 0.5 * vec_norm(cross)


def validate_outer_scale(matrix, boss_id):
    scale = matrix[0][0]
    if not math.isfinite(scale) or scale <= 0:
        raise ValueError(f"{boss_id}: outer_scale_matrix has invalid scale {scale}")
    for row in range(3):
        for column in range(3):
            expected = scale if row == column else 0.0
            if abs(matrix[row][column] - expected) > 1e-6:
                raise ValueError(
                    f"{boss_id}: outer_scale_matrix is not a positive uniform scale"
                )
    return scale


def require_mapping(value, label):
    if not isinstance(value, dict):
        raise ValueError(f"{label}: expected an object")
    return value


def require_list(value, label):
    if not isinstance(value, list):
        raise ValueError(f"{label}: expected a list")
    return value


def validate_rig(rig, label):
    rig = require_mapping(rig, label)
    names = require_list(rig.get("rig_names"), label + ".rig_names")
    parents = require_list(rig.get("rig_parents"), label + ".rig_parents")
    worlds = require_list(rig.get("rig_world"), label + ".rig_world")
    if len(names) != 16 or len(parents) != 16 or len(worlds) != 16:
        raise ValueError(f"{label}: expected 16 joints, got {len(names)}/{len(parents)}/{len(worlds)}")
    for index, parent in enumerate(parents):
        if not isinstance(parent, int) or parent < -1 or parent >= 16 or parent == index:
            raise ValueError(f"{label}: invalid rig_parents[{index}]={parent!r}")
    frames = []
    for index, world in enumerate(worlds):
        frames.append(mat_from_column_major(world, f"{label}.rig_world[{index}]"))
    return names, parents, frames


def build_penguin_rig(builder, frames, parents, names, h):
    groups_by_index = {}
    pending = set(range(16))
    safety = 0
    while pending:
        safety += 1
        if safety > 64:
            raise ValueError(f"{builder.model_id}: penguin rig hierarchy is cyclic")
        progressed = False
        for index in sorted(pending):
            parent_index = parents[index]
            if parent_index == -1:
                parent = None
            elif parent_index in groups_by_index:
                parent = groups_by_index[parent_index]
            else:
                continue
            key = f"penguin/{index}"
            group = builder.add_group(
                key,
                builder.unique_group_name(f"penguin_{names[index]}"),
                *rigid_from_matrix(mat_mul(mat_mul(h, frames[index]), C_MATRIX), key),
                parent,
            )
            groups_by_index[index] = group
            pending.remove(index)
            progressed = True
        if not progressed:
            raise ValueError(f"{builder.model_id}: penguin rig parents are not resolvable")
    groups = [groups_by_index[index] for index in range(16)]
    if len(groups) != 16:
        raise ValueError(f"{builder.model_id}: expected 16 penguin joints")
    builder.penguin_joint_count = len(groups)
    return groups


def anchor_joint_for_face(points, face, joint_count):
    scores = [0.0] * joint_count
    for point_index in (face["a"], face["b"], face["c"]):
        if not isinstance(point_index, int) or point_index < 0 or point_index >= len(points):
            raise ValueError(f"skin face references invalid point {point_index!r}")
        point = points[point_index]
        a = point.get("a")
        b = point.get("b")
        weight = point.get("weight")
        if not isinstance(a, int) or not isinstance(b, int):
            raise ValueError("skin point has invalid joint references")
        if a < 0 or a >= joint_count or b < 0 or b >= joint_count:
            raise ValueError("skin point joint index out of range")
        if not isinstance(weight, (int, float)) or not math.isfinite(float(weight)):
            raise ValueError("skin point has a non-finite weight")
        scores[a] += 1.0 - float(weight)
        scores[b] += float(weight)
    best = 0
    for index in range(1, joint_count):
        if scores[index] > scores[best] + 1e-12:
            best = index
    return best


def add_skin_geometry(builder, source, h, rig_groups, palette_texture, face_groups=None):
    points = require_list(source.get("points"), builder.model_id + ".points")
    faces = require_list(source.get("faces"), builder.model_id + ".faces")
    if not points or not faces:
        raise ValueError(f"{builder.model_id}: skin source has no points/faces")

    world_points = []
    for index, point in enumerate(points):
        point = require_mapping(point, f"{builder.model_id}.points[{index}]")
        position = point.get("position")
        if not isinstance(position, list) or len(position) != 3:
            raise ValueError(f"{builder.model_id}.points[{index}]: invalid position")
        world = mat_point(h, position)
        if not all(math.isfinite(v) for v in world):
            raise ValueError(f"{builder.model_id}.points[{index}]: non-finite world position")
        world_points.append(world)

    triangles_by_joint = [[] for _ in range(len(rig_groups))]
    auxiliary = {}
    for face_index, face in enumerate(faces):
        face = require_mapping(face, f"{builder.model_id}.faces[{face_index}]")
        try:
            u = float(face.get("u"))
            v = float(face.get("v"))
        except (TypeError, ValueError) as exc:
            raise ValueError(f"{builder.model_id}.faces[{face_index}]: invalid UV") from exc
        if not math.isfinite(u) or not math.isfinite(v):
            raise ValueError(f"{builder.model_id}.faces[{face_index}]: non-finite UV")
        anchor = anchor_joint_for_face(points, face, len(rig_groups))
        point_indices = (face["a"], face["b"], face["c"])
        a = world_points[point_indices[0]]
        b = world_points[point_indices[1]]
        c = world_points[point_indices[2]]
        if triangle_area(a, b, c) < 1e-12:
            continue
        uv = palette_texture.palette_uv(u, v)
        triangle = [(world_points[index], uv) for index in point_indices]
        extra_group = face_groups.get(face_index) if face_groups else None
        if extra_group is None:
            triangles_by_joint[anchor].append(triangle)
        else:
            auxiliary.setdefault(extra_group.uuid, (extra_group, []))[1].append(triangle)

    for joint_index, triangles in enumerate(triangles_by_joint):
        if not triangles:
            continue
        group = rig_groups[joint_index]
        builder.add_triangles(
            group,
            f"skin/{joint_index}",
            triangles,
            f"{group.name}_skin",
        )
    for group, triangles in auxiliary.values():
        builder.add_triangles(group, 'skin/'+group.name, triangles, group.name+'_skin')


def ignis_belly_face_groups(builder, source, h, rig_groups):
    """Reparent captured body faces only; cuffs keep the original 16-joint rig."""
    contract = require_mapping(source.get('belly_wings'), 'Ignis belly-wing descriptor')
    if contract.get('schema') != 1 or source.get('mesh_sha256') != contract.get('runtime_mesh_sha256'):
        raise ValueError('Ignis capture/wing descriptor hash mismatch; recapture the current mesh')
    segments = require_list(contract.get('segments'), 'Ignis segments')
    if len(segments) != 6 or contract.get('body_base_triangle_count') != 268:
        raise ValueError('Ignis requires exactly six auxiliary wing bones and unchanged base body')
    body_world = mat_from_column_major(source.get('body_world'), 'Ignis body_world')
    by_ordinal = {}
    for index, face in enumerate(source['faces']):
        if face.get('source_part') == 'body':
            ordinal = face.get('source_triangle')
            if not isinstance(ordinal, int) or ordinal in by_ordinal:
                raise ValueError('Invalid captured body face provenance')
            by_ordinal[ordinal] = index
    if set(by_ordinal) != set(range(len(by_ordinal))):
        raise ValueError('Missing captured body face provenance')
    parents, ownership = {'body': rig_groups[1]}, {}
    expected_ids = {'belly_'+s+'_'+n for s in ('right', 'left') for n in ('upper', 'fore', 'tip')}
    if {s['id'] for s in segments} != expected_ids:
        raise ValueError('Invalid Ignis auxiliary joint names')
    counts = {}
    for segment in segments:
        name = segment['id']
        expected_parent = 'body' if segment['name'] == 'upper' else 'belly_'+segment['side']+'_'+('upper' if segment['name'] == 'fore' else 'fore')
        if segment['parent'] != expected_parent or expected_parent not in parents:
            raise ValueError('Invalid Ignis auxiliary hierarchy')
        pivot = segment['pivot']
        frame = mat_mul(mat_mul(mat_mul(h, body_world), mat_translate(*pivot)), C_MATRIX)
        group = builder.add_group(name, builder.unique_group_name(name), *rigid_from_matrix(frame, name), parents[expected_parent])
        parents[name] = group
        indices = segment['body_triangles']
        if not indices:
            raise ValueError('Empty Ignis auxiliary wing segment')
        counts[name] = len(indices)
        for ordinal in indices:
            if not isinstance(ordinal, int) or ordinal < 268 or ordinal not in by_ordinal:
                raise ValueError('Auxiliary wing ownership touches base body or unknown face')
            index = by_ordinal[ordinal]
            if index in ownership:
                raise ValueError('Duplicate Ignis wing face ownership')
            ownership[index] = group
    if set(ownership) != {by_ordinal[i] for i in range(268, len(by_ordinal))}:
        raise ValueError('Incomplete Ignis wing face ownership')
    builder.belly_wing_bone_count = 6
    builder.belly_wing_triangle_counts = counts
    return ownership


def native_quad_triangles(node, node_matrix, h, texture, include_geometry):
    if not include_geometry:
        return []
    quads = node.get("quads") or []
    if not isinstance(quads, list):
        raise ValueError(f"node {node.get('key')}: quads is not a list")
    world_matrix = mat_mul(h, node_matrix)
    triangles = []
    for quad_index, quad in enumerate(quads):
        if not isinstance(quad, list) or len(quad) != 4:
            raise ValueError(f"node {node.get('key')}: quad {quad_index} is not 4 vertices")
        corners = []
        for vertex in quad:
            if not isinstance(vertex, list) or len(vertex) != 5:
                raise ValueError(f"node {node.get('key')}: malformed quad vertex")
            world = mat_point(world_matrix, vertex[:3])
            if not all(math.isfinite(v) for v in world):
                raise ValueError(f"node {node.get('key')}: non-finite quad world point")
            corners.append((world, texture.base_uv(vertex[3], vertex[4])))
        for indices in ((0, 1, 2), (0, 2, 3)):
            a = corners[indices[0]][0]
            b = corners[indices[1]][0]
            c = corners[indices[2]][0]
            if triangle_area(a, b, c) < 1e-12:
                continue
            triangles.append([corners[indices[0]], corners[indices[1]], corners[indices[2]]])
    return triangles


def node_geometry_override(node, boss_id, weapon_mode):
    key = str(node.get("key", ""))
    if boss_id == "maledictus" and weapon_mode in ("maces", "bow"):
        segments = set(key.split("/"))
        bow = bool(segments & {"bow", "halberd"})
        mace = bool(segments & {"right_mace", "left_mace"})
        if weapon_mode == "maces":
            if bow:
                return False
            if mace:
                return True
        else:
            if mace:
                return False
            if bow:
                return True
    return bool(node.get("visible")) and bool(node.get("draw"))


def build_original(br, boss_id, boss, h, texture, weapon_mode=None):
    nodes = require_list(boss.get("nodes"), f"{boss_id}.nodes")
    node_by_key = {}
    for node in nodes:
        node = require_mapping(node, f"{boss_id}.node")
        key = node.get("key")
        if not isinstance(key, str) or not key:
            raise ValueError(f"{boss_id}: node without a key")
        if key in node_by_key:
            raise ValueError(f"{boss_id}: duplicate node key {key}")
        node_by_key[key] = node

    name_counts = {}
    for node in node_by_key.values():
        name = node.get("name") or node["key"].split("/")[-1]
        name_counts[name] = name_counts.get(name, 0) + 1

    groups = {}
    visiting = set()

    def ensure(key):
        if key in groups:
            return groups[key]
        if key in visiting:
            raise ValueError(f"{boss_id}: node hierarchy is cyclic at {key}")
        visiting.add(key)
        node = node_by_key[key]
        parent_key = node.get("parent")
        if parent_key in (None, "", 0):
            parent = None
        else:
            if not isinstance(parent_key, str) or parent_key not in node_by_key:
                raise ValueError(f"{boss_id}: node {key} has missing parent {parent_key!r}")
            parent = ensure(parent_key)
        frame = rigid_from_matrix(
            mat_mul(mat_mul(h, mat_from_column_major(node.get("world"), f"{boss_id}.{key}.world")), C_MATRIX),
            f"{boss_id}.{key}",
        )
        if name_counts.get(node.get("name") or key.split("/")[-1], 0) == 1:
            base_name = node.get("name") or key.split("/")[-1]
        else:
            base_name = key.replace("/", "_")
        group = br.add_group(key, br.unique_group_name(base_name), frame[0], frame[1], parent)
        groups[key] = group
        visiting.remove(key)
        return group

    for key in node_by_key:
        ensure(key)

    for key, node in node_by_key.items():
        world = mat_from_column_major(node.get("world"), f"{boss_id}.{key}.world")
        include = node_geometry_override(node, boss_id, weapon_mode)
        triangles = native_quad_triangles(node, world, h, texture, include)
        if triangles:
            br.add_triangles(groups[key], key, triangles, f"{groups[key].name}_mesh")

    br.native_bone_count = len(node_by_key)
    return br


def subtree_weapon_key(key):
    segments = set(key.split("/"))
    return bool(segments & {"bow", "halberd", "right_mace", "left_mace"})


def build_joint_map(joint_native_keys, boss_id):
    if not isinstance(joint_native_keys, list) or len(joint_native_keys) != 16:
        raise ValueError(f"{boss_id}: joint_native_keys must contain 16 paths")
    mapping = {}
    for joint_index, path in enumerate(joint_native_keys):
        if not isinstance(path, str) or not path:
            raise ValueError(f"{boss_id}: joint_native_keys[{joint_index}] is invalid")
        mapping[path] = max(mapping.get(path, -1), joint_index)
    return mapping


def attachment_joint(boss_id, key, node_by_key, joint_map):
    if boss_id == "ignis":
        tail = key.split("/")[-1]
        if tail == "lowerbody2":
            return 0
        if tail == "head":
            return 3
        if tail == "left_arm":
            return 4
        if tail == "right_arm":
            return 7
    current = key
    while isinstance(current, str) and current:
        if current in joint_map:
            return joint_map[current]
        node = node_by_key.get(current)
        if node is None:
            break
        parent = node.get("parent")
        current = parent if isinstance(parent, str) else None
    return 0


def build_penguin_form(
    model_id,
    boss_id,
    boss,
    h,
    texture,
    palette_texture,
    body_only,
    weapon_mode=None,
):
    br = Builder(model_id, texture)
    penguin = require_mapping(boss.get("penguin"), f"{boss_id}.penguin")
    names, parents, frames = validate_rig(penguin, f"{boss_id}.penguin")
    rig_groups = build_penguin_rig(br, frames, parents, names, h)
    face_groups = ignis_belly_face_groups(br, penguin, h, rig_groups) if boss_id == 'ignis' else None
    add_skin_geometry(br, penguin, h, rig_groups, palette_texture, face_groups)

    if not body_only:
        nodes = require_list(boss.get("nodes"), f"{boss_id}.nodes")
        node_by_key = {}
        for node in nodes:
            node = require_mapping(node, f"{boss_id}.node")
            key = node.get("key")
            if not isinstance(key, str) or not key:
                raise ValueError(f"{boss_id}: node without a key")
            node_by_key[key] = node
        retained = require_list(boss.get("retained_native"), f"{boss_id}.retained_native")
        retained_set = set()
        for key in retained:
            if not isinstance(key, str) or key not in node_by_key:
                raise ValueError(f"{boss_id}: retained_native references unknown node {key!r}")
            retained_set.add(key)
        joint_map = build_joint_map(boss.get("joint_native_keys"), boss_id)

        name_counts = {}
        for key in retained_set:
            name = node_by_key[key].get("name") or key.split("/")[-1]
            name_counts[name] = name_counts.get(name, 0) + 1

        groups = {}
        visiting = set()

        def ensure(key):
            if key in groups:
                return groups[key]
            if key in visiting:
                raise ValueError(f"{boss_id}: retained hierarchy is cyclic at {key}")
            visiting.add(key)
            node = node_by_key[key]
            parent_key = node.get("parent")
            parent_group = None
            if isinstance(parent_key, str):
                probe = parent_key
                while isinstance(probe, str) and probe not in retained_set:
                    parent_node = node_by_key.get(probe)
                    probe = parent_node.get("parent") if parent_node else None
                if isinstance(probe, str) and probe in retained_set:
                    parent_group = ensure(probe)
            if parent_group is None:
                joint_index = attachment_joint(boss_id, key, node_by_key, joint_map)
                parent_group = rig_groups[joint_index]
            frame = rigid_from_matrix(
                mat_mul(
                    mat_mul(h, mat_from_column_major(node.get("world"), f"{boss_id}.{key}.world")),
                    C_MATRIX,
                ),
                f"{boss_id}.{key}",
            )
            if name_counts.get(node.get("name") or key.split("/")[-1], 0) == 1:
                base_name = node.get("name") or key.split("/")[-1]
            else:
                base_name = key.replace("/", "_")
            group = br.add_group(
                key,
                br.unique_group_name("retainednative_" + base_name),
                frame[0],
                frame[1],
                parent_group,
            )
            groups[key] = group
            visiting.remove(key)
            return group

        for key in retained:
            ensure(key)

        for key in retained:
            node = node_by_key[key]
            world = mat_from_column_major(node.get("world"), f"{boss_id}.{key}.world")
            include = node_geometry_override(node, boss_id, weapon_mode)
            triangles = native_quad_triangles(node, world, h, texture, include)
            if triangles:
                br.add_triangles(groups[key], key, triangles, f"{groups[key].name}_mesh")
        br.native_bone_count = len(retained_set)
    return br


def build_penguin_high(model_id, data, h, palette_image):
    texture = make_palette_texture(palette_image)
    br = Builder(model_id, texture)
    names, parents, frames = validate_rig(data, model_id)
    rig_groups = build_penguin_rig(br, frames, parents, names, h)
    add_skin_geometry(br, data, h, rig_groups, texture)
    return br


# ---------------------------------------------------------------------------
# Finalisation / validation
# ---------------------------------------------------------------------------


def group_absolute_frame(group, cache):
    """Reconstruct (R, T) from the serialized Euler rotations and origins."""
    if group.uuid in cache:
        return cache[group.uuid]
    local = mat3_from_euler_deg(group.rotation)
    if group.parent is None:
        frame = (local, list(group.origin))
    else:
        parent_r, parent_t = group_absolute_frame(group.parent, cache)
        origin = vec_sub(group.origin, group.parent.origin)
        frame = (mat3_mul(parent_r, local), vec_add(parent_t, mat3_vec(parent_r, origin)))
    cache[group.uuid] = frame
    return frame


def validate_builder(builder):
    group_uuids = {group.uuid for group in builder.groups}
    if len(group_uuids) != len(builder.groups):
        raise ValueError(f"{builder.model_id}: duplicate group UUID")
    names = [group.name for group in builder.groups]
    if len(set(names)) != len(names):
        raise ValueError(f"{builder.model_id}: duplicate group name")

    assigned = set()
    for group in builder.groups:
        if group.parent is not None and group.parent.uuid not in group_uuids:
            raise ValueError(f"{builder.model_id}: group parent is missing")
        if group.parent is not None:
            parent_children = {
                child if isinstance(child, str) else child.uuid for child in group.parent.children
            }
            if group.uuid not in parent_children:
                raise ValueError(f"{builder.model_id}: parent does not list child {group.key}")
        for child in group.children:
            key = child if isinstance(child, str) else child.uuid
            if not isinstance(child, str) and child.parent is not group:
                raise ValueError(f"{builder.model_id}: child {key} has an inconsistent parent")
            if key in assigned:
                raise ValueError(f"{builder.model_id}: child assigned twice: {key}")
            assigned.add(key)
    element_uuids = {element["uuid"] for element in builder.elements}
    # Strings are mesh element UUIDs; Group objects are non-root group UUIDs.
    expected = element_uuids | {
        group.uuid for group in builder.groups if group.parent is not None
    }
    if assigned != expected:
        missing = sorted(expected - assigned)
        unknown = sorted(assigned - expected)
        raise ValueError(
            f"{builder.model_id}: child assignment mismatch (missing {missing}, unknown {unknown})"
        )

    # Acyclic check plus reconstruction from the serialized group hierarchy.
    visiting = set()
    done = set()

    def walk(group):
        if group.uuid in done:
            return
        if group.uuid in visiting:
            raise ValueError(f"{builder.model_id}: group hierarchy is cyclic")
        visiting.add(group.uuid)
        for child in group.children:
            if not isinstance(child, str):
                walk(child)
        visiting.remove(group.uuid)
        done.add(group.uuid)

    for group in builder.groups:
        if group.parent is None:
            walk(group)
    if done != group_uuids:
        raise ValueError(f"{builder.model_id}: not all groups are reachable from a root")

    cache = {}
    element_by_uuid = {element["uuid"]: element for element in builder.elements}
    max_error = 0.0
    for element_uuid, vertex_id, expected in builder.records:
        group = builder.element_groups[element_uuid]
        element = element_by_uuid[element_uuid]
        local = element["vertices"][vertex_id]
        rotation, translation = group_absolute_frame(group, cache)
        world = vec_add(translation, mat3_vec(rotation, local))
        error = max(abs(world[i] - expected[i]) for i in range(3))
        max_error = max(max_error, error)
    if max_error > RECONSTRUCTION_TOLERANCE:
        raise ValueError(
            f"{builder.model_id}: BB group reconstruction error {max_error:g} exceeds "
            f"{RECONSTRUCTION_TOLERANCE:g} model pixels"
        )

    for element in builder.elements:
        for face in element["faces"].values():
            if len(face["vertices"]) != 3:
                raise ValueError(f"{builder.model_id}: non-triangle face")
            for vertex_id in face["vertices"]:
                if vertex_id not in element["vertices"]:
                    raise ValueError(f"{builder.model_id}: face references missing vertex")
                uv = face["uv"][vertex_id]
                if not all(math.isfinite(v) for v in uv):
                    raise ValueError(f"{builder.model_id}: non-finite UV")
                if uv[0] < 0 or uv[1] < 0 or uv[0] > builder.texture.width or uv[1] > builder.texture.height:
                    raise ValueError(f"{builder.model_id}: UV outside texture")

    return max_error


def bounds_for_builder(builder):
    if not builder.records:
        return None
    minimum = [float("inf")] * 3
    maximum = [float("-inf")] * 3
    for _, _, world in builder.records:
        for axis in range(3):
            minimum[axis] = min(minimum[axis], world[axis])
            maximum[axis] = max(maximum[axis], world[axis])
    return {
        "min": [round(v, 6) for v in minimum],
        "max": [round(v, 6) for v in maximum],
    }


def world_samples(builder, limit=3):
    records = builder.records
    if not records:
        return []
    if len(records) <= limit:
        indexes = range(len(records))
    else:
        indexes = sorted({0, len(records) // 2, len(records) - 1})
    samples = []
    for index in indexes:
        element_uuid, vertex_id, world = records[index]
        samples.append(
            {
                "elementUUID": element_uuid,
                "vertexKey": vertex_id,
                "expectedWorldXYZ": [round(v, 6) for v in world],
            }
        )
    return samples


def build_bbmodel_json(builder):
    groups_json = []
    for group in builder.groups:
        groups_json.append(
            {
                "name": group.name,
                "uuid": group.uuid,
                "export": True,
                "locked": False,
                "visibility": True,
                "isOpen": False,
                "origin": [round(v, 9) for v in group.origin],
                "rotation": [round(v, 9) for v in group.rotation],
                # Blockbench 5 keeps hierarchy in the outliner; groups[] children stay empty.
                "children": [],
            }
        )

    def outliner_node(group):
        children = []
        for child in group.children:
            if isinstance(child, str):
                children.append(child)
            else:
                children.append(outliner_node(child))
        return {"uuid": group.uuid, "isOpen": False, "children": children}

    outliner = [
        outliner_node(group) for group in builder.groups if group.parent is None
    ]

    texture_uuid = str(uuid.uuid5(UUID_NS, f"{builder.model_id}/texture"))
    source = "data:image/png;base64," + base64.b64encode(png_bytes(builder.texture.image)).decode("ascii")
    document = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": builder.model_id,
        "model_identifier": "",
        "resolution": {"width": builder.texture.width, "height": builder.texture.height},
        "elements": builder.elements,
        "groups": groups_json,
        "outliner": outliner,
        "textures": [
            {
                "name": "texture.png",
                "path": "",
                "folder": "",
                "namespace": "",
                "id": "0",
                "group": "",
                "width": builder.texture.width,
                "height": builder.texture.height,
                "uv_width": builder.texture.width,
                "uv_height": builder.texture.height,
                "particle": False,
                "use_as_default": False,
                "layers_enabled": False,
                "sync_to_project": "",
                "file_format": "png",
                "render_mode": "default",
                "render_sides": "auto",
                "visible": True,
                "internal": True,
                "saved": False,
                "uuid": texture_uuid,
                "source": source,
            }
        ],
    }
    return document


def write_model(builder, out_dir, texture_png, filename=None, directory_id=None):
    max_error = validate_builder(builder)
    document = build_bbmodel_json(builder)
    model_dir = out_dir / (directory_id or builder.model_id)
    model_dir.mkdir(parents=True, exist_ok=True)
    model_name = filename or f"{builder.model_id}.bbmodel"
    model_path = model_dir / model_name
    texture_path = model_dir / ("texture_soul.png" if filename and "soul" in filename else "texture.png")
    model_path.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    texture_path.write_bytes(texture_png)
    return {
        "file": model_path.relative_to(out_dir).as_posix(),
        "texture": texture_path.relative_to(out_dir).as_posix(),
        "max_reconstruction_error": max_error,
    }


# ---------------------------------------------------------------------------
# Model catalogue
# ---------------------------------------------------------------------------


C_MATRIX = mat_diag(-1.0, -1.0, 1.0)
G_MATRIX = mat_translate(0.0, 24.0, 0.0)


def boss_transform(boss, boss_id):
    outer = mat_from_column_major(boss.get("outer_scale_matrix"), f"{boss_id}.outer_scale_matrix")
    validate_outer_scale(outer, boss_id)
    return mat_mul(outer, mat_mul(G_MATRIX, C_MATRIX))


def display_path(path):
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def make_summary_entry(builder, write_info, description):
    return {
        "id": builder.model_id,
        "description": description,
        "file": write_info["file"],
        "texture": write_info["texture"],
        "triangle_count": builder.triangle_count,
        "mesh_count": builder.mesh_count,
        "group_count": len(builder.groups),
        "native_bone_count": builder.native_bone_count,
        "penguin_joint_count": builder.penguin_joint_count,
        "belly_wing_bone_count": getattr(builder, 'belly_wing_bone_count', 0),
        "belly_wing_triangle_counts": getattr(builder, 'belly_wing_triangle_counts', {}),
        "texture_dims": {"width": builder.texture.width, "height": builder.texture.height},
        "bounds": bounds_for_builder(builder),
        "max_reconstruction_error": write_info["max_reconstruction_error"],
        "world_samples": world_samples(builder),
    }


def run(args):
    input_path = Path(args.input)
    textures_dir = Path(args.textures)
    out_dir = Path(args.out)

    if not input_path.is_file():
        raise FileNotFoundError(f"input scene not found: {input_path}")
    if not textures_dir.is_dir():
        raise FileNotFoundError(f"textures directory not found: {textures_dir}")

    scene = json.loads(input_path.read_text(encoding="utf-8"))
    if not isinstance(scene, dict):
        raise ValueError("native scene root must be an object")
    bosses = require_mapping(scene.get("bosses"), "bosses")
    for boss_id in BOSS_ORDER:
        if boss_id not in bosses:
            raise ValueError(f"native scene is missing boss {boss_id}")
    if "penguin_high" not in scene:
        raise ValueError("native scene is missing penguin_high")

    palette_image = load_rgba(textures_dir / PALETTE_NAME)
    boss_images = {}
    for boss_id in BOSS_ORDER:
        boss_images[boss_id] = load_rgba(textures_dir / f"{boss_id}.png")

    out_dir.mkdir(parents=True, exist_ok=True)

    summary_models = []
    auxiliary = []

    def emit(builder, description, filename=None, directory_id=None):
        texture_png = png_bytes(builder.texture.image)
        info = write_model(builder, out_dir, texture_png, filename=filename, directory_id=directory_id)
        return make_summary_entry(builder, info, description)

    # Originals use the corresponding composited boss texture unchanged.
    for boss_id in BOSS_ORDER:
        boss = require_mapping(bosses[boss_id], f"bosses.{boss_id}")
        h = boss_transform(boss, boss_id)
        texture = make_base_texture(boss_images[boss_id])
        weapon_mode = "maces" if boss_id == "maledictus" else None
        builder = build_original(
            Builder(f"{boss_id}_original", texture),
            boss_id,
            boss,
            h,
            texture,
            weapon_mode=weapon_mode,
        )
        summary_models.append(
            emit(
                builder,
                f"Full native {boss_id} hierarchy with native quads and the composited boss texture.",
            )
        )

        if args.weapon_variants and boss_id == "maledictus":
            variant_texture = make_base_texture(boss_images[boss_id])
            variant = build_original(
                Builder(f"{boss_id}_original_bow", variant_texture),
                boss_id,
                boss,
                h,
                variant_texture,
                weapon_mode="bow",
            )
            auxiliary.append(
                emit(
                    variant,
                    "Maledictus native hierarchy with bow/halberd geometry enabled and maces hidden.",
                )
            )

    # Full and body-only penguin forms for each boss.
    for boss_id in BOSS_ORDER:
        boss = require_mapping(bosses[boss_id], f"bosses.{boss_id}")
        h = boss_transform(boss, boss_id)
        base_image = boss_images[boss_id]
        atlas = make_atlas_texture(base_image, palette_image)
        palette = make_palette_texture(palette_image)
        weapon_mode = "maces" if boss_id == "maledictus" else None

        full = build_penguin_form(
            f"{boss_id}_penguin",
            boss_id,
            boss,
            h,
            atlas,
            atlas,
            body_only=False,
            weapon_mode=weapon_mode,
        )
        summary_models.append(
            emit(
                full,
                f"Penguin skin plus retained {boss_id} native head/weapons/ornaments on the 16-joint penguin rig.",
            )
        )

        if args.weapon_variants and boss_id == "maledictus":
            variant_atlas = make_atlas_texture(base_image, palette_image)
            variant = build_penguin_form(
                f"{boss_id}_penguin_bow",
                boss_id,
                boss,
                h,
                variant_atlas,
                variant_atlas,
                body_only=False,
                weapon_mode="bow",
            )
            auxiliary.append(
                emit(
                    variant,
                    "Maledictus penguin form with retained bow/halberd geometry and maces hidden.",
                )
            )

        body_only = build_penguin_form(
            f"{boss_id}_penguin_body_only",
            boss_id,
            boss,
            h,
            palette,
            palette,
            body_only=True,
        )
        summary_models.append(
            emit(
                body_only,
                f"Penguin-only replacement body using the {boss_id} skin, without retained native parts.",
            )
        )

        if args.ignis_soul and boss_id == "ignis":
            soul_image = load_rgba(textures_dir / SOUL_NAME)
            if soul_image.size != base_image.size:
                raise ValueError(
                    "ignis_soul.png must have the same dimensions as ignis.png for identical UV layout"
                )
            soul_texture = make_base_texture(soul_image)
            soul_original = build_original(
                Builder("ignis_original_soul", soul_texture),
                boss_id,
                boss,
                h,
                soul_texture,
                weapon_mode=None,
            )
            auxiliary.append(
                emit(
                    soul_original,
                    "Ignis native hierarchy using the soul-idle composited texture.",
                    filename="ignis_original_soul.bbmodel",
                    directory_id="ignis_original",
                )
            )
            soul_atlas = make_atlas_texture(soul_image, palette_image)
            if (soul_atlas.width, soul_atlas.height) != (atlas.width, atlas.height):
                raise ValueError("Ignis soul atlas layout differs from the main Ignis atlas")
            soul_full = build_penguin_form(
                "ignis_penguin_soul",
                boss_id,
                boss,
                h,
                soul_atlas,
                soul_atlas,
                body_only=False,
                weapon_mode=None,
            )
            auxiliary.append(
                emit(
                    soul_full,
                    "Ignis penguin form using the soul-idle composited base plus the shared palette.",
                    filename="ignis_penguin_soul.bbmodel",
                    directory_id="ignis_penguin",
                )
            )

    # Penguin high: game (0.75 block) and raw 24-pixel authoring variants.
    high = require_mapping(scene["penguin_high"], "penguin_high")
    game_h = mat_mul(G_MATRIX, mat_mul(C_MATRIX, mat_mul(mat_translate(0.0, 12.0, 0.0), mat_scale(0.5))))
    authoring_h = mat_mul(G_MATRIX, C_MATRIX)
    high_game = build_penguin_high("penguin_high", high, game_h, palette_image)
    summary_models.append(
        emit(
            high_game,
            "Penguin-only game body, scaled so the native 24-pixel body becomes 12 pixels / 0.75 block.",
        )
    )
    high_authoring = build_penguin_high("penguin_high_authoring", high, authoring_h, palette_image)
    summary_models.append(
        emit(
            high_authoring,
            "Penguin-only authoring body at the raw 24 native model pixels.",
        )
    )

    summary = {
        "schema": 1,
        "generated_by": "tools/export_bbs_models.py",
        "input": display_path(input_path),
        "textures_dir": display_path(textures_dir),
        "matrix_layout": "column-major input, row-major output, native Y-down, floor 24",
        "coordinate_contract": {
            "boss_H": "outer_scale_matrix * translate(0,24,0) * diag(-1,-1,1,1)",
            "penguin_authoring_H": "translate(0,24,0) * diag(-1,-1,1,1)",
            "penguin_game_H": "translate(0,24,0) * diag(-1,-1,1,1) * translate(0,12,0) * scale(0.5)",
            "bone_frame": "rigid(H * nodeWorld * diag(-1,-1,1,1))",
            "mesh_vertex": "transpose(F.R) * (worldPoint - F.T)",
            "reconstruction_tolerance_model_pixels": RECONSTRUCTION_TOLERANCE,
        },
        "primary_model_count": len(summary_models),
        "models": summary_models,
        "auxiliary_models": auxiliary,
        "limitations": [
            "Skin weights are baked into rigid per-face groups; this is not continuous BBS skinning.",
            "No native actions/animations are converted.",
            "Hidden native weapon bones are kept as empty groups; geometry follows visible&&draw.",
            "Raw third-party textures stay under ignored dev-libs and are not runtime assets.",
        ],
    }
    summary_path = out_dir / "summary.json"
    summary_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"exported {len(summary_models)} primary model(s) and {len(auxiliary)} auxiliary model(s)")
    for entry in summary_models + auxiliary:
        print(
            f"  {entry['id']}: {entry['triangle_count']} tris, "
            f"{entry['mesh_count']} meshes, {entry['group_count']} groups, "
            f"texture {entry['texture_dims']['width']}x{entry['texture_dims']['height']}"
        )
    print(f"summary: {display_path(summary_path)}")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", default=str(DEFAULT_INPUT), help="native_scene.json path")
    parser.add_argument("--textures", default=str(DEFAULT_TEXTURES), help="PIL-composited texture directory")
    parser.add_argument("--out", default=str(DEFAULT_OUT), help="output directory (never deleted)")
    parser.add_argument(
        "--weapon-variants",
        action="store_true",
        help="also write Maledictus bow/halberd variants",
    )
    parser.add_argument(
        "--ignis-soul",
        action="store_true",
        help="also write Ignis soul-texture bbmodel variants",
    )
    args = parser.parse_args(argv)
    try:
        return run(args)
    except Exception as exc:  # noqa: BLE001 - CLI must report the concrete failure
        print(f"BBS export failed: {exc}", file=sys.stderr)
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
