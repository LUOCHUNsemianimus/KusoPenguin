#!/usr/bin/env python3
"""Offline Source SMD -> penguin dance exporter. Standard library only.

The runtime never reads SMD, ValveBiped binds or this script. It parses the two imported
clips once during the normal client resource reload; every frame then uses PenguinAnimationClip
and the shared PenguinAnimationPose.evaluate path.

Source conventions (ValveBiped): forward -Y, up +Z, right -X.
Penguin model convention: forward -Z, up -Y (model +Y points down), right +X.
Therefore the vector basis is C = (-sourceX, -sourceZ, +sourceY). This is an
improper reflection (det=-1) because the two conventions differ by handedness.
Rotations are conjugated C R C^-1. A reflection has no unit-quaternion
representation, so the vector part maps as det(C)*C*q.xyz = (qx, qz, -qy) with
w unchanged, then normalized. Neutral orientation is taken from the exported MDL
bone bind, never an animation first frame.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "models/animation/gmod/source"
DEFAULT_OUT = ROOT / "src/main/resources/assets/kuso_penguin/animations"
DEFAULT_REPORT = ROOT / "models/animation/gmod/getdown_scan_report.json"
RIG_PATH = ROOT / "models/animation/penguin_rig.json"

POLE_SMD = "Amod_AM4_Spooky_Month_Dance_male.smd"
POLE_BIND = "add_taunt-male-bind.json"
GETDOWN_SMD = "Amod_MMD_GetDown_male.smd"
GETDOWN_BIND = "add_mmd-male-bind.json"
MANIFEST = "animations/manifest.json"

# Keep these numbers in sync with PenguinDancePose: sample at the far edge of the tiny
# parser-satisfying blend-in so every inner-loop sample has full weight.
BLEND_IN = 0.001
BLEND_OUT = 0.001
FPS = 30.0
SOURCE_LOOP_FLAG = 1

TARGET_PELVIS, TARGET_CHEST, TARGET_HEAD = 1, 2, 3
TARGET_WINGS = {1: (4, 5, 6), -1: (7, 8, 9)}
TARGET_LEGS = {1: (10, 11, 12), -1: (13, 14, 15)}

SRC = {
    "pelvis": "ValveBiped.Bip01_Pelvis",
    "chest": "ValveBiped.Bip01_Spine4",
    "head": "ValveBiped.Bip01_Head1",
    "r_up": "ValveBiped.Bip01_R_UpperArm",
    "r_fore": "ValveBiped.Bip01_R_Forearm",
    "r_hand": "ValveBiped.Bip01_R_Hand",
    "r_finger2": "ValveBiped.Bip01_R_Finger2",
    "l_up": "ValveBiped.Bip01_L_UpperArm",
    "l_fore": "ValveBiped.Bip01_L_Forearm",
    "l_hand": "ValveBiped.Bip01_L_Hand",
    "l_finger2": "ValveBiped.Bip01_L_Finger2",
    "r_thigh": "ValveBiped.Bip01_R_Thigh",
    "r_calf": "ValveBiped.Bip01_R_Calf",
    "r_foot": "ValveBiped.Bip01_R_Foot",
    "r_toe": "ValveBiped.Bip01_R_Toe0",
    "l_thigh": "ValveBiped.Bip01_L_Thigh",
    "l_calf": "ValveBiped.Bip01_L_Calf",
    "l_foot": "ValveBiped.Bip01_L_Foot",
    "l_toe": "ValveBiped.Bip01_L_Toe0",
}

# Reflection from Source axes to penguin model axes. det(C) = -1; it must never be
# represented by a unit quaternion. Rotations are conjugated as C R C^-1.
C_MATRIX = ((-1.0, 0.0, 0.0), (0.0, 0.0, -1.0), (0.0, 1.0, 0.0))


def qnorm(q):
    x, y, z, w = q
    n = math.sqrt(x * x + y * y + z * z + w * w)
    if not math.isfinite(n) or n < 1.0e-12:
        raise ValueError("cannot normalize zero/non-finite quaternion")
    return (x / n, y / n, z / n, w / n)


def qmul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz,
    )


def qconj(q):
    return (-q[0], -q[1], -q[2], q[3])


def qrot(q, v):
    x, y, z, w = q
    vx, vy, vz = v
    tx = 2.0 * (y * vz - z * vy)
    ty = 2.0 * (z * vx - x * vz)
    tz = 2.0 * (x * vy - y * vx)
    return (
        vx + w * tx + (y * tz - z * ty),
        vy + w * ty + (z * tx - x * tz),
        vz + w * tz + (x * ty - y * tx),
    )


def qangle(a, b):
    """Shortest angular distance in degrees between two orientations."""
    dot = abs(sum(x * y for x, y in zip(qnorm(a), qnorm(b))))
    return math.degrees(2.0 * math.acos(max(-1.0, min(1.0, dot))))


def rotation_between(a, b):
    def unit(v):
        n = math.sqrt(sum(c * c for c in v))
        if n < 1.0e-12:
            raise ValueError("zero direction")
        return tuple(c / n for c in v)

    a, b = unit(a), unit(b)
    dot = max(-1.0, min(1.0, sum(x * y for x, y in zip(a, b))))
    if dot > 1.0 - 1.0e-12:
        return (0.0, 0.0, 0.0, 1.0)
    if dot < -1.0 + 1.0e-12:
        axis = (1.0, 0.0, 0.0) if abs(a[0]) < 0.9 else (0.0, 1.0, 0.0)
        c = (a[1] * axis[2] - a[2] * axis[1],
             a[2] * axis[0] - a[0] * axis[2],
             a[0] * axis[1] - a[1] * axis[0])
        c = unit(c)
        return qnorm((c[0], c[1], c[2], 0.0))
    c = (a[1] * b[2] - a[2] * b[1],
         a[2] * b[0] - a[0] * b[2],
         a[0] * b[1] - a[1] * b[0])
    return qnorm((c[0], c[1], c[2], 1.0 + dot))


def euler_rz_ry_rx(x, y, z):
    hx, hy, hz = x * 0.5, y * 0.5, z * 0.5
    qx = (math.sin(hx), 0.0, 0.0, math.cos(hx))
    qy = (0.0, math.sin(hy), 0.0, math.cos(hy))
    qz = (0.0, 0.0, math.sin(hz), math.cos(hz))
    return qnorm(qmul(qmul(qz, qy), qx))


def qfrommatrix(m):
    """Row-major 3x3 rotation -> unit quaternion (x,y,z,w)."""
    tr = m[0][0] + m[1][1] + m[2][2]
    if tr > 0.0:
        s = math.sqrt(tr + 1.0) * 2.0
        q = ((m[2][1] - m[1][2]) / s, (m[0][2] - m[2][0]) / s,
             (m[1][0] - m[0][1]) / s, 0.25 * s)
    elif m[0][0] > m[1][1] and m[0][0] > m[2][2]:
        s = math.sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]) * 2.0
        q = (0.25 * s, (m[0][1] + m[1][0]) / s,
             (m[0][2] + m[2][0]) / s, (m[2][1] - m[1][2]) / s)
    elif m[1][1] > m[2][2]:
        s = math.sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]) * 2.0
        q = ((m[0][1] + m[1][0]) / s, 0.25 * s,
             (m[1][2] + m[2][1]) / s, (m[0][2] - m[2][0]) / s)
    else:
        s = math.sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]) * 2.0
        q = ((m[0][2] + m[2][0]) / s, (m[1][2] + m[2][1]) / s,
             0.25 * s, (m[1][0] - m[0][1]) / s)
    return qnorm(q)


def qmatrix(q):
    x, y, z, w = qnorm(q)
    return (
        (1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)),
        (2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)),
        (2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)),
    )


def mat_vec(m, v):
    return (m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2])


def mat_mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3))
                 for i in range(3))


def mat_transpose(m):
    return tuple(tuple(m[j][i] for j in range(3)) for i in range(3))


def mat_det(m):
    return (m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
            - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
            + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]))


def cvec(v):
    """Source vector -> target vector: (-sourceX, -sourceZ, +sourceY)."""
    return mat_vec(C_MATRIX, v)


def c_rotate(q):
    """Conjugate a Source rotation by the reflection C.

    C R C^-1 is proper, but C itself has no unit-quaternion representation.
    For q=(x,y,z,w) the conjugated vector part is det(C)*C*q.xyz = (x,z,-y);
    the scalar part is unchanged. The result is normalized for clip output.
    """
    x, y, z, w = qnorm(q)
    return qnorm((x, z, -y, w))


def c_matrix(m):
    """Row-major 3x3 Source rotation -> target rotation matrix C R C^-1."""
    return mat_mul(mat_mul(C_MATRIX, m), mat_transpose(C_MATRIX))


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def mul(v, s):
    return (v[0] * s, v[1] * s, v[2] * s)


def length(v):
    return math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])


def dist(a, b):
    return length(sub(a, b))


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def parse_smd(path):
    text = path.read_text(encoding="utf-8-sig")
    if "skeleton" not in text or "nodes" not in text:
        raise ValueError(f"{path}: missing nodes/skeleton")
    header, skeleton = text.split("skeleton", 1)
    nodes = {}
    for m in re.finditer(r'^\s*(\d+)\s+"([^"]+)"\s+(-?\d+)\s*$', header, re.M):
        nodes[int(m.group(1))] = (m.group(2), int(m.group(3)))
    if not nodes:
        raise ValueError(f"{path}: no nodes")
    frames = []
    pat = re.compile(r"^\s*time\s+(\d+)\s*\n(.*?)(?=^\s*time\s+\d+\s*$|^end\s*$)", re.M | re.S)
    for m in pat.finditer(skeleton):
        t = int(m.group(1))
        if t != len(frames):
            raise ValueError(f"{path}: non-contiguous frame time {t}, expected {len(frames)}")
        local = {}
        for line in m.group(2).splitlines():
            row = line.split()
            if not row:
                continue
            if len(row) != 7:
                raise ValueError(f"{path}: frame {t} expected 7 values, got {len(row)}")
            i = int(row[0])
            vals = [float(v) for v in row[1:]]
            if not all(math.isfinite(v) for v in vals):
                raise ValueError(f"{path}: non-finite frame {t} bone {i}")
            local[i] = ((vals[0], vals[1], vals[2]),
                        euler_rz_ry_rx(vals[3], vals[4], vals[5]))
        missing = set(nodes) - set(local)
        if missing:
            raise ValueError(f"{path}: frame {t} missing nodes {sorted(missing)}")
        frames.append(local)
    if len(frames) < 2:
        raise ValueError(f"{path}: fewer than two frames")
    return nodes, frames


def parse_bind(path):
    doc = json.loads(path.read_text(encoding="utf-8"))
    bones = doc.get("bones", [])
    by_name = {}
    for b in bones:
        name = b["name"]
        if name in by_name:
            raise ValueError(f"{path}: duplicate bone {name}")
        pos = tuple(float(v) for v in b["position"])
        quat = qnorm(tuple(float(v) for v in b["rotation_xyzw"]))
        if not all(math.isfinite(v) for v in pos + quat):
            raise ValueError(f"{path}: non-finite bind {name}")
        by_name[name] = {"id": int(b["id"]), "parent": int(b["parent"]),
                         "position": pos, "rotation": quat}
    for name, b in by_name.items():
        if b["parent"] >= 0:
            parent_name = next((n for n, x in by_name.items() if x["id"] == b["parent"]), None)
            if parent_name is None:
                raise ValueError(f"{path}: bone {name} has missing parent {b['parent']}")
            b["parent_name"] = parent_name
        else:
            b["parent_name"] = None
    return by_name


def fk(nodes, local):
    world = {}

    def visit(i):
        if i in world:
            return world[i]
        name, parent = nodes[i]
        p, q = local[i]
        if parent < 0:
            world[i] = (p, qnorm(q))
        else:
            pp, pq = visit(parent)
            world[i] = (add(pp, qrot(pq, p)), qnorm(qmul(pq, q)))
        return world[i]

    for i in nodes:
        visit(i)
    return world


def nodes_from_bind(bind):
    """Use bind-bone parent ids but SMD-equivalent id/name hierarchy for rest FK."""
    by_id = {b["id"]: (name, b["parent"]) for name, b in bind.items()}
    if sorted(by_id) != list(range(len(by_id))):
        raise ValueError("bind bone ids are not contiguous")
    return by_id


def bind_local(bind):
    return {b["id"]: (b["position"], b["rotation"]) for b in bind.values()}


class Retargeter:
    def __init__(self, bind, rig):
        self.bind = bind
        self.rig = rig
        self.rest_bind_nodes = nodes_from_bind(bind)
        self.rest = fk(self.rest_bind_nodes, bind_local(bind))
        self.bid = {name: b["id"] for name, b in bind.items()}
        self.bind_pos = {i: tuple(float(v) for v in j["bind"])
                         for i, j in enumerate(rig["joints"])}
        # Anatomical ratio from pelvis-to-head rest distance. It scales only local body
        # displacement, never joint lengths or world/entity translation.
        self.scale = (dist(self.bind_pos[TARGET_HEAD], self.bind_pos[TARGET_PELVIS])
                      / dist(self.rest[self.bid[SRC["head"]]][0],
                             self.rest[self.bid[SRC["pelvis"]]][0]))
        # Fixed target canonical bind link vectors. The directional goal below maps these
        # vectors onto the corrected-C Source animated FK direction; it never rotates a
        # Source rest direction and then applies that swing to a different target bind.
        self.target_links = {}
        for side in (1, -1):
            wing, mid, tip = TARGET_WINGS[side]
            hip, knee, foot = TARGET_LEGS[side]
            self.target_links[("wing", side, "upper")] = sub(
                self.bind_pos[mid], self.bind_pos[wing])
            self.target_links[("wing", side, "lower")] = sub(
                self.bind_pos[tip], self.bind_pos[mid])
            self.target_links[("leg", side, "thigh")] = sub(
                self.bind_pos[knee], self.bind_pos[hip])
            self.target_links[("leg", side, "calf")] = sub(
                self.bind_pos[foot], self.bind_pos[knee])

        # Actual visible wing-tip axis used by PenguinHeldItemRenderer.ACTIVE_TIP_OFFSET,
        # including the negative-wing X mirror. This is not a guessed extension of the bone.
        self.visible_tip_offsets = {
            1: (0.78, 2.0, 0.17),
            -1: (-0.78, 2.0, 0.17),
        }
        # Fixed neutral calibration: rotate the real visible tip offset onto the Source
        # Hand->Finger2 rest direction. Source Finger2 local position is constant in every
        # reviewed clip, so this fixed calibration is exact rather than an approximation.
        self.tip_rest_cal = {}
        for side in (1, -1):
            hand = "r_hand" if side > 0 else "l_hand"
            finger = "r_finger2" if side > 0 else "l_finger2"
            rest_finger = cvec(sub(self.rest[self.bid[SRC[finger]]][0],
                                   self.rest[self.bid[SRC[hand]]][0]))
            self.tip_rest_cal[side] = rotation_between(
                self.visible_tip_offsets[side], rest_finger)

    def tip_goal(self, world, hand_key, side):
        """Calibrated target tip world rotation: Source hand twist plus neutral tip axis."""
        return qnorm(qmul(self.target_delta(world, hand_key), self.tip_rest_cal[side]))

    def target_delta(self, world, key):
        q = qnorm(qmul(world[self.bid[SRC[key]]][1],
                       qconj(self.rest[self.bid[SRC[key]]][1])))
        return c_rotate(q)

    def source_direction(self, world, a, b):
        """Corrected-C Source animated FK link direction in target model space."""
        v = cvec(sub(world[self.bid[SRC[b]]][0], world[self.bid[SRC[a]]][0]))
        n = length(v)
        if n < 1.0e-12:
            raise ValueError(f"degenerate Source link {a}->{b}")
        return mul(v, 1.0 / n)

    def aim(self, target_vec, desired):
        """Rotation that maps the fixed target canonical bind link onto the Source motion."""
        return rotation_between(target_vec, desired)

    def frame_direction_report(self, world, out):
        """Compare achieved target world limb directions with corrected-C Source FK."""
        q = out["rotations"]
        errors = {"right": {}, "left": {}, "leg_right": {}, "leg_left": {}}

        def residual(achieved, desired):
            a = mul(achieved, 1.0 / length(achieved))
            dot = max(-1.0, min(1.0, sum(x * y for x, y in zip(a, desired))))
            return math.degrees(math.acos(dot))

        for side in (1, -1):
            up, fore, hand = "r_up", "r_fore", "r_hand"
            finger = "r_finger2"
            thigh, calf, foot = "r_thigh", "r_calf", "r_foot"
            if side < 0:
                up, fore, hand = "l_up", "l_fore", "l_hand"
                finger = "l_finger2"
                thigh, calf, foot = "l_thigh", "l_calf", "l_foot"
            wing, mid, tip = TARGET_WINGS[side]
            # q[] holds local rotations. Runtime chest orientation is pelvis * local chest.
            q_chest_world = qnorm(qmul(q[TARGET_PELVIS], q[TARGET_CHEST]))
            q_wing_world = qnorm(qmul(q_chest_world, q[wing]))
            q_mid_world = qnorm(qmul(q_wing_world, q[mid]))
            q_tip_world = qnorm(qmul(q_mid_world, q[tip]))
            side_name = "right" if side > 0 else "left"
            errors[side_name]["upper"] = residual(
                qrot(q_wing_world, self.target_links[("wing", side, "upper")]),
                self.source_direction(world, up, fore))
            errors[side_name]["lower"] = residual(
                qrot(q_mid_world, self.target_links[("wing", side, "lower")]),
                self.source_direction(world, fore, hand))
            errors[side_name]["hand_orientation"] = math.degrees(2.0 * math.acos(
                max(-1.0, min(1.0, abs(sum(x * y for x, y in zip(
                    q_tip_world, self.tip_goal(world, hand, side))))))))
            visible_offset = self.visible_tip_offsets[side]
            achieved_tip = qrot(q_tip_world, visible_offset)
            errors[side_name]["tip_direction"] = residual(
                achieved_tip, self.source_direction(world, hand, finger))
            errors[side_name]["tip_offset_length_delta"] = abs(
                length(achieved_tip) - length(visible_offset))

            hip, knee, toe = TARGET_LEGS[side]
            q_thigh_world = q[hip]
            q_calf_world = qnorm(qmul(q_thigh_world, q[knee]))
            q_foot_world = qnorm(qmul(q_calf_world, q[toe]))
            leg_name = "leg_right" if side > 0 else "leg_left"
            errors[leg_name]["thigh"] = residual(
                qrot(q_thigh_world, self.target_links[("leg", side, "thigh")]),
                self.source_direction(world, thigh, calf))
            errors[leg_name]["calf"] = residual(
                qrot(q_calf_world, self.target_links[("leg", side, "calf")]),
                self.source_direction(world, calf, foot))
            errors[leg_name]["foot_orientation"] = math.degrees(2.0 * math.acos(
                max(-1.0, min(1.0, abs(sum(x * y for x, y in zip(
                    q_foot_world, self.target_delta(world, foot))))))))
        return errors

    def frame(self, frame, pelvis_origin=None):
        world = fk(self.rest_bind_nodes, frame)
        q = [(0.0, 0.0, 0.0, 1.0) for _ in range(16)]
        p = [(0.0, 0.0, 0.0) for _ in range(16)]
        q_pelvis = self.target_delta(world, "pelvis")
        q_chest = self.target_delta(world, "chest")
        q_head = self.target_delta(world, "head")
        origin = (self.rest[self.bid[SRC["pelvis"]]][0]
                  if pelvis_origin is None else pelvis_origin)
        src_delta = cvec(sub(world[self.bid[SRC["pelvis"]]][0], origin))
        pelvis_pos = add(self.bind_pos[TARGET_PELVIS], mul(src_delta, self.scale))
        chest_pos = add(pelvis_pos, qrot(q_pelvis,
                                         sub(self.bind_pos[TARGET_CHEST],
                                             self.bind_pos[TARGET_PELVIS])))
        head_pos = add(chest_pos, qrot(q_chest,
                                       sub(self.bind_pos[TARGET_HEAD],
                                           self.bind_pos[TARGET_CHEST])))
        q[TARGET_PELVIS] = q_pelvis
        p[TARGET_PELVIS] = sub(pelvis_pos, self.bind_pos[TARGET_PELVIS])
        q[TARGET_CHEST] = qnorm(qmul(qconj(q_pelvis), q_chest))
        q[TARGET_HEAD] = qnorm(qmul(qconj(q_chest), q_head))

        for side in (1, -1):
            up, fore, hand = "r_up", "r_fore", "r_hand"
            thigh, calf, foot = "r_thigh", "r_calf", "r_foot"
            if side < 0:
                up, fore, hand = "l_up", "l_fore", "l_hand"
                thigh, calf, foot = "l_thigh", "l_calf", "l_foot"
            wing, mid, tip = TARGET_WINGS[side]
            shoulder = add(chest_pos, qrot(q_chest,
                                           sub(self.bind_pos[wing],
                                               self.bind_pos[TARGET_CHEST])))
            upper_vec = self.target_links[("wing", side, "upper")]
            lower_vec = self.target_links[("wing", side, "lower")]
            # Absolute desired directions from corrected-C Source animated FK.
            desired_upper = self.source_direction(world, up, fore)
            desired_lower = self.source_direction(world, fore, hand)
            # Rotate the TARGET canonical bind link onto the Source motion. The previous
            # code swung the Source rest direction instead, which left the 20.99..21.86 deg
            # residual because the target bind link differs from the Source neutral link.
            q_upper = self.aim(upper_vec, desired_upper)
            q_lower_world = self.aim(lower_vec, desired_lower)
            elbow = add(shoulder, qrot(q_upper, upper_vec))
            wrist = add(elbow, qrot(q_lower_world, lower_vec))
            # Preserve Source hand twist, then apply the fixed neutral calibration that aligns
            # the ACTUAL visible tip offset with the Source Hand->Finger2 rest direction.
            q_tip_world = self.tip_goal(world, hand, side)
            # Derive each local quaternion from its actual parent exactly once.
            q[wing] = qnorm(qmul(qconj(q_chest), q_upper))
            q[mid] = qnorm(qmul(qconj(q_upper), q_lower_world))
            q[tip] = qnorm(qmul(qconj(q_lower_world), q_tip_world))
            # Keep the fixed target lengths explicit; the wrist is informational.
            if abs(dist(shoulder, elbow) - length(upper_vec)) > 1.0e-9:
                raise ValueError("wing upper link length changed")
            if abs(dist(elbow, wrist) - length(lower_vec)) > 1.0e-9:
                raise ValueError("wing lower link length changed")

            hip, knee, toe = TARGET_LEGS[side]
            thigh_vec = self.target_links[("leg", side, "thigh")]
            calf_vec = self.target_links[("leg", side, "calf")]
            desired_thigh = self.source_direction(world, thigh, calf)
            desired_calf = self.source_direction(world, calf, foot)
            q_thigh_world = self.aim(thigh_vec, desired_thigh)
            q_calf_world = self.aim(calf_vec, desired_calf)
            q_foot_world = self.target_delta(world, foot)
            q[hip] = q_thigh_world
            q[knee] = qnorm(qmul(qconj(q_thigh_world), q_calf_world))
            q[toe] = qnorm(qmul(qconj(q_calf_world), q_foot_world))
            # Foot world positions are rebuilt by PenguinLegPose's anatomical rebind; the
            # exported local rotations still follow Source FK. All clip translations remain
            # zero except the pelvis, so no independent root travel is introduced.
        return {
            "rotations": [list(qq) for qq in q],
            "translations": [list(pp) for pp in p],
            "scales": [[1.0, 1.0, 1.0] for _ in range(16)],
        }


def joints(rig):
    return [{"name": j["name"], "parent": j["parent"], "bind": j["bind"]}
            for j in rig["joints"]]


def clip_doc(name, source, source_sha, source_bind, source_hash, frames, duration,
             runtime_rate, extra, rig):
    doc = {
        "schema": 1,
        "name": name,
        "source": source,
        "source_sha256": source_sha,
        "source_bind": source_bind,
        "source_bind_sha256": source_hash,
        "source_format": "ValveBiped SMD; local XYZ Euler radians evaluated as Rz*Ry*Rx",
        "basis": ("target=(-sourceX,-sourceZ,+sourceY) det=-1 reflection; rotations C R C^-1, "
                  "q.xyz->(x,z,-y); source forward -Y up +Z right -X"),
        "runtime_rate": runtime_rate,
        "blend_in": BLEND_IN,
        "blend_out": BLEND_OUT,
        "duration": duration,
        "joints": joints(rig),
        "frames": frames,
    }
    doc.update(extra)
    return doc


def mean_pelvis_origin(nodes, source_frames):
    pid = next(i for i, (name, _) in nodes.items() if name == SRC["pelvis"])
    points = [fk(nodes, frame)[pid][0] for frame in source_frames]
    return (sum(p[0] for p in points) / len(points),
            sum(p[1] for p in points) / len(points),
            sum(p[2] for p in points) / len(points))


def source_frames_to_clip(retargeter, source_frames, times, pelvis_origin):
    frames = []
    for t, f in zip(times, source_frames):
        out = retargeter.frame(f, pelvis_origin)
        frames.append({"time": t, **out})
    return frames


def direction_validation(retargeter, source_frames, pelvis_origin, label):
    """Concise FK proof: achieved target vs corrected-C Source directions and fixed lengths."""
    summary = {
        "label": label,
        "frames": len(source_frames),
        "max_wing_direction_residual_deg": {"right": 0.0, "left": 0.0},
        "max_tip_direction_residual_deg": {"right": 0.0, "left": 0.0},
        "max_hand_orientation_residual_deg": {"right": 0.0, "left": 0.0},
        "max_leg_direction_residual_deg": {"right": 0.0, "left": 0.0},
        "max_foot_orientation_residual_deg": {"right": 0.0, "left": 0.0},
        "max_target_link_length_delta": 0.0,
        "max_tip_offset_length_delta": 0.0,
        "visible_tip_offsets": {str(side): list(offset)
                                for side, offset in retargeter.visible_tip_offsets.items()},
        "tolerance_deg": 1.0e-3,
    }
    for f in source_frames:
        world = fk(retargeter.rest_bind_nodes, f)
        out = retargeter.frame(f, pelvis_origin)
        report = retargeter.frame_direction_report(world, out)
        for side in ("right", "left"):
            wing = report[side]
            leg = report["leg_" + side]
            summary["max_wing_direction_residual_deg"][side] = max(
                summary["max_wing_direction_residual_deg"][side],
                wing["upper"], wing["lower"])
            summary["max_tip_direction_residual_deg"][side] = max(
                summary["max_tip_direction_residual_deg"][side],
                wing["tip_direction"])
            summary["max_hand_orientation_residual_deg"][side] = max(
                summary["max_hand_orientation_residual_deg"][side],
                wing["hand_orientation"])
            summary["max_tip_offset_length_delta"] = max(
                summary["max_tip_offset_length_delta"],
                wing["tip_offset_length_delta"])
            summary["max_leg_direction_residual_deg"][side] = max(
                summary["max_leg_direction_residual_deg"][side],
                leg["thigh"], leg["calf"])
            summary["max_foot_orientation_residual_deg"][side] = max(
                summary["max_foot_orientation_residual_deg"][side],
                leg["foot_orientation"])
        for side in (1, -1):
            wing, mid, tip = TARGET_WINGS[side]
            hip, knee, foot = TARGET_LEGS[side]
            for key, a, b in (("upper", wing, mid), ("lower", mid, tip),
                              ("thigh", hip, knee), ("calf", knee, foot)):
                bind_len = dist(retargeter.bind_pos[a], retargeter.bind_pos[b])
                link_len = length(retargeter.target_links[
                    ("wing" if key in ("upper", "lower") else "leg", side, key)])
                summary["max_target_link_length_delta"] = max(
                    summary["max_target_link_length_delta"], abs(bind_len - link_len))
    if summary["max_target_link_length_delta"] > 1.0e-9:
        raise ValueError(f"{label}: target canonical link length changed")
    if summary["max_tip_offset_length_delta"] > 1.0e-9:
        raise ValueError(f"{label}: target visible tip offset length changed")
    limit = summary["tolerance_deg"]
    for key in ("max_wing_direction_residual_deg", "max_tip_direction_residual_deg",
                "max_hand_orientation_residual_deg", "max_leg_direction_residual_deg",
                "max_foot_orientation_residual_deg"):
        for side in ("right", "left"):
            if summary[key][side] > limit:
                raise ValueError(f"{label}: {key} {side} = "
                                 f"{summary[key][side]:.9f} deg > {limit}")
    return summary


def scan_candidates(pelvis, heads, hands, quats):
    """Return qualifying repeated hip intervals, newest first, with source metrics."""
    n = len(pelvis)
    # Source forward is -Y, side/right is -X; sign reversal does not change peak-to-peak
    # amplitude or the selected interval, only the signed axis label.
    fwd = [-p[1] for p in pelvis]
    side = [-p[0] for p in pelvis]
    up = [p[2] for p in pelvis]
    candidates = []
    # Search every frame and every allowed period. We iterate end backwards so the selected
    # interval is genuinely the latest qualifying repeat, not simply the global maximum.
    for end in range(n - 1, 5, -1):
        for period in range(30, 5, -1):
            start = end - period
            if start < 0:
                continue
            seg = slice(start, end + 1)
            pf = fwd[seg]
            amp = max(pf) - min(pf)
            if amp < 0.8:
                continue
            side_amp = max(side[seg]) - min(side[seg])
            up_amp = max(up[seg]) - min(up[seg])
            if amp < 1.15 * max(side_amp, up_amp):
                continue
            head_pts = heads[seg]
            head_c = (sum(p[0] for p in head_pts) / len(head_pts),
                      sum(p[1] for p in head_pts) / len(head_pts),
                      sum(p[2] for p in head_pts) / len(head_pts))
            head_exc = max(dist(p, head_c) for p in head_pts) * 2.0
            hand_rel_exc = 0.0
            hand_world_exc = 0.0
            for hand in hands:
                pts = hand[seg]
                rel_pts = [sub(hand[i], heads[i]) for i in range(start, end + 1)]
                cr = (sum(p[0] for p in rel_pts) / len(rel_pts),
                      sum(p[1] for p in rel_pts) / len(rel_pts),
                      sum(p[2] for p in rel_pts) / len(rel_pts))
                hand_rel_exc = max(hand_rel_exc, 2.0 * max(dist(p, cr) for p in rel_pts))
                c = (sum(p[0] for p in pts) / len(pts),
                     sum(p[1] for p in pts) / len(pts),
                     sum(p[2] for p in pts) / len(pts))
                hand_world_exc = max(hand_world_exc, 2.0 * max(dist(p, c) for p in pts))
            if head_exc > 0.9 * amp or hand_rel_exc > 0.65 * amp:
                continue
            endpoint_forward = abs(pf[-1] - pf[0])
            if endpoint_forward > 0.25 * amp:
                continue
            closure_angle = max(qangle(quats[start][i], quats[end][i])
                                for i in quats[0])
            if closure_angle > 12.0:
                continue
            closure_head = dist(heads[start], heads[end])
            if closure_head > 0.5 * amp + 0.25:
                continue
            # Prefer clear fore/aft thrust, stable upper body and a clean repeat.
            score = amp / (1.0 + head_exc + hand_rel_exc) + 0.02 * (amp - side_amp)
            candidates.append({
                "first_source_frame": start,
                "last_source_frame": end,
                "sample_count": end - start + 1,
                "period_seconds": (end - start) / FPS,
                "pelvis_forward_amplitude": amp,
                "pelvis_side_amplitude": side_amp,
                "pelvis_up_amplitude": up_amp,
                "head_world_excursion": head_exc,
                "hand_world_excursion": hand_world_exc,
                "hand_relative_head_excursion": hand_rel_exc,
                "endpoint_pelvis_forward_mismatch": endpoint_forward,
                "endpoint_head_position_mismatch": closure_head,
                "endpoint_max_world_rotation_deg": closure_angle,
                "score": score,
            })
    # Latest qualifying end wins. Among periods at that end, use the strongest geometry.
    by_end = {}
    for c in candidates:
        by_end.setdefault(c["last_source_frame"], []).append(c)
    ordered = []
    for end in sorted(by_end, reverse=True):
        ordered.extend(sorted(by_end[end], key=lambda c: c["score"], reverse=True))
    return ordered


def requested_interval_metrics(pelvis, heads, hands, quats, start, end):
    seg = slice(start, end + 1)
    pf = [-p[1] for p in pelvis[seg]]
    head_pts = heads[seg]
    c = (sum(p[0] for p in head_pts) / len(head_pts),
         sum(p[1] for p in head_pts) / len(head_pts),
         sum(p[2] for p in head_pts) / len(head_pts))
    head_exc = max(dist(p, c) for p in head_pts) * 2.0
    hand_rel = 0.0
    for hand in hands:
        rp = [sub(hand[i], heads[i]) for i in range(start, end + 1)]
        rc = (sum(p[0] for p in rp) / len(rp), sum(p[1] for p in rp) / len(rp),
              sum(p[2] for p in rp) / len(rp))
        hand_rel = max(hand_rel, 2.0 * max(dist(p, rc) for p in rp))
    return {
        "first_source_frame": start,
        "last_source_frame": end,
        "period_seconds": (end - start) / FPS,
        "pelvis_forward_amplitude": max(pf) - min(pf),
        "head_world_excursion": head_exc,
        "hand_relative_head_excursion": hand_rel,
        "endpoint_pelvis_forward_mismatch": abs(pf[-1] - pf[0]),
        "endpoint_max_world_rotation_deg": max(
            qangle(quats[start][i], quats[end][i]) for i in quats[0]),
    }


def build_scan(source_dir):
    getdown_path = source_dir / "animations" / GETDOWN_SMD
    nodes, smd_frames = parse_smd(getdown_path)
    bind = parse_bind(source_dir / GETDOWN_BIND)
    by_name = {name: i for i, (name, _) in nodes.items()}
    if set(by_name) != set(bind):
        raise ValueError("GetDown SMD node names do not match MDL bind")
    local_frames = []
    for f in smd_frames:
        local_frames.append({i: f[i] for i in nodes})
    pelvis, heads, hands, quats = [], [], [], []
    for f in local_frames:
        w = fk(nodes, f)
        pelvis.append(w[by_name[SRC["pelvis"]]][0])
        heads.append(w[by_name[SRC["head"]]][0])
        hands.append((w[by_name[SRC["r_hand"]]][0], w[by_name[SRC["l_hand"]]][0]))
        quats.append({i: w[i][1] for i in w})
    # Turn hand pairs into two per-frame arrays for the scanner.
    hand0 = [h[0] for h in hands]
    hand1 = [h[1] for h in hands]
    ordered = scan_candidates(pelvis, heads, (hand0, hand1), quats)
    requested = requested_interval_metrics(pelvis, heads, (hand0, hand1), quats, 1167, 1182)
    if not ordered:
        raise SystemExit("ERROR: no qualifying 0.2..1.0 s pelvis fore/aft repeat found")
    chosen = ordered[0]
    return {
        "source": str(getdown_path),
        "source_sha256": sha256(getdown_path),
        "bind": str(source_dir / GETDOWN_BIND),
        "bind_sha256": sha256(source_dir / GETDOWN_BIND),
        "source_frames": len(smd_frames),
        "fps": FPS,
        "source_axis_basis": "forward -Y, up +Z, right -X",
        "target_basis": ("forward -Z, up -Y (model +Y down), right +X; "
                         "C=(-X,-Z,+Y), det=-1 reflection, rotations C R C^-1"),
        "signed_axis_labels": {
            "forward": "-sourceY", "right": "-sourceX", "up": "+sourceZ",
            "note": ("Sign reversal does not change peak-to-peak amplitudes or interval "
                     "selection; amplitudes below remain unsigned magnitudes."),
        },
        "allowed_period_seconds": [0.2, 1.0],
        "requested_candidate_1167_1182": requested,
        "requested_candidate_verdict": (
            "Rejected as hip motion: pelvis/spine/upperarms/legs static in this interval; "
            "only head/hands rotate. This is the same failure the upper task measured."
        ),
        "chosen": chosen,
        "latest_qualifying": ordered[0],
        "top_candidates_newest_first": ordered[:20],
        "top_candidates_by_score": sorted(ordered, key=lambda c: c["score"], reverse=True)[:10],
        "metrics_note": (
            "All values are Source world FK, not local Euler. Forward amplitude is pelvis "
            "world -Y (Source forward) peak-to-peak; side is -X. Head/hand excursion is 2x "
            "max distance from the segment centroid; hand-relative-head subtracts head "
            "position per frame."
        ),
        "chosen_quality_checks": {
            "forward_amplitude_source_units": chosen["pelvis_forward_amplitude"],
            "forward_dominates_side": chosen["pelvis_forward_amplitude"]
            > 1.15 * chosen["pelvis_side_amplitude"],
            "head_stable_ratio": chosen["head_world_excursion"]
            / chosen["pelvis_forward_amplitude"],
            "hands_stable_relative_head_ratio": chosen["hand_relative_head_excursion"]
            / chosen["pelvis_forward_amplitude"],
            "endpoint_rotation_deg": chosen["endpoint_max_world_rotation_deg"],
            "endpoint_pelvis_forward_mismatch": chosen[
                "endpoint_pelvis_forward_mismatch"],
        },
    }


def pole_build(source_dir, retargeter, rig):
    path = source_dir / "animations" / POLE_SMD
    nodes, src = parse_smd(path)
    bind = parse_bind(source_dir / POLE_BIND)
    if set(n for n, _ in nodes.values()) != set(bind):
        raise ValueError("Spooky SMD node names do not match MDL bind")
    if len(src) != 10:
        raise ValueError(f"Spooky expected 10 original frames, got {len(src)}")
    times = [i / FPS for i in range(10)]
    # Constant world/rest offsets are not motion. Keep pelvis translation as a zero-mean local
    # displacement, like the existing dance pose semantics, rather than pushing the whole body
    # away from its anatomical bind. Rotations still use the immutable MDL neutral rest.
    pelvis_origin = mean_pelvis_origin(nodes, src[:10])
    frames = source_frames_to_clip(retargeter, src, times, pelvis_origin)
    # Explicit repeat-first endpoint: source frame 10 == source frame 0 at 10/30 s.
    first = retargeter.frame(src[0], pelvis_origin)
    times.append(10.0 / FPS)
    frames.append({"time": times[-1], **first})
    duration = 10.0 / FPS
    # Report the real source seam (frame 9 -> frame 0), not a fake identity claim.
    w9 = fk(nodes, src[9])
    w0 = fk(nodes, src[0])
    seam = max(qangle(w9[i][1], w0[i][1]) for i in nodes)
    seam_pos = max(dist(w9[i][0], w0[i][0]) for i in nodes)
    direction = direction_validation(
        retargeter, src[:10] + [src[0]], pelvis_origin, "pole")
    extra = {
        "direction_validation": direction,
        "runtime_rate_source": 0.8,
        "runtime_rate_note": (
            "Original plugin Rate=0.8 applies to this imported clip only; the old procedural "
            "1.5x multiplier is not applied. The real five-second style and 8-second revival "
            "gameplay clocks are unchanged."
        ),
        "source_fps": FPS,
        "source_original_frames": 10,
        "exported_frames": 11,
        "loop": (
            "Source loop flag 1. Original frames 0..9 preserved, then an explicit repeat of "
            "frame 0 is written at 10/30 s so sample() wraps continuously. This is a deliberate "
            "closure; the original frame 9 -> frame 0 seam is reported separately."
        ),
        "original_loop_seam_max_angle_deg": seam,
        "original_loop_seam_pelvis_position": seam_pos,
        "clip_first_last_angle_deg": 0.0,
        "source_bind_frames": 56,
        "pelvis_translation_reference": (
            "mean Source world pelvis over the ten original frames; pelvis translation is "
            "zero-mean local displacement, root/world/entity translation is zero"),
    }
    doc = clip_doc("gmod_spooky_pole", "GMod add_taunt male Amod_AM4_Spooky_Month_Dance",
                   sha256(path), POLE_BIND, sha256(source_dir / POLE_BIND),
                   frames, duration, 0.8, extra, rig)
    return doc, {
        "original_loop_seam_max_angle_deg": seam,
        "original_loop_seam_pelvis_position": seam_pos,
        "clip_first_last_angle_deg": 0.0,
    }


def hip_build(source_dir, retargeter, scan, rig):
    path = source_dir / "animations" / GETDOWN_SMD
    nodes, src = parse_smd(path)
    bind = parse_bind(source_dir / GETDOWN_BIND)
    if set(n for n, _ in nodes.values()) != set(bind):
        raise ValueError("GetDown SMD node names do not match MDL bind")
    c = scan["chosen"]
    start, end = c["first_source_frame"], c["last_source_frame"]
    times = [i / FPS for i in range(end - start + 1)]
    selected = src[start:end + 1]
    pelvis_origin = mean_pelvis_origin(nodes, selected)
    frames = source_frames_to_clip(retargeter, selected, times, pelvis_origin)
    duration = (end - start) / FPS
    # The searched interval closes in pelvis forward but may retain a small upper-body seam.
    # Preserve the source end; report the actual seam instead of claiming an exact loop.
    direction = direction_validation(retargeter, selected, pelvis_origin, "hip")
    extra = {
        "direction_validation": direction,
        "runtime_rate_source": 1.0,
        "runtime_rate_note": "Source metrics justify rate 1.0; no procedural 1.5x multiplier.",
        "source_fps": FPS,
        "source_original_frames": 1198,
        "selected_source_frame_range": [start, end],
        "selected_source_samples": end - start + 1,
        "loop": (
            "Selected repeated Source range only; final source sample preserved. The next "
            "runtime loop wraps to the first sample. Seam metrics below are measured, not "
            "normalized away."
        ),
        "pelvis_translation_reference": (
            "mean Source world pelvis over the selected repeat; pelvis translation is zero-mean "
            "local displacement, root/world/entity translation is zero"),
        "seam": {
            "endpoint_pelvis_forward_mismatch": c["endpoint_pelvis_forward_mismatch"],
            "endpoint_head_position_mismatch": c["endpoint_head_position_mismatch"],
            "endpoint_max_world_rotation_deg": c["endpoint_max_world_rotation_deg"],
            "synthetic_endpoint_added": False,
        },
    }
    doc = clip_doc("gmod_getdown_hip", "GMod add_mmd male Amod_MMD_GetDown selected repeat",
                   sha256(path), GETDOWN_BIND, sha256(source_dir / GETDOWN_BIND),
                   frames, duration, 1.0, extra, rig)
    return doc, extra["seam"]


def validate_clip(doc, rig, label):
    errors = []
    if doc.get("schema") != 1:
        errors.append(f"{label}: schema != 1")
    js = doc.get("joints", [])
    if len(js) != 16:
        errors.append(f"{label}: joint count {len(js)} != 16")
    for i, (a, b) in enumerate(zip(js, rig["joints"])):
        if a.get("name") != b["name"] or a.get("parent") != b["parent"]:
            errors.append(f"{label}: joint contract mismatch at {i}")
        if [float(v) for v in a.get("bind", [])] != [float(v) for v in b["bind"]]:
            errors.append(f"{label}: bind mismatch at {i}")
    frames = doc.get("frames", [])
    if len(frames) < 2:
        errors.append(f"{label}: fewer than 2 frames")
    last_t = -1.0
    max_rot = 0.0
    max_pelvis = 0.0
    for f in frames:
        t = float(f["time"])
        if not math.isfinite(t) or t <= last_t:
            errors.append(f"{label}: non-increasing time")
        last_t = t
        for key in ("rotations", "translations", "scales"):
            if len(f[key]) != 16:
                errors.append(f"{label}: {key} length != 16 at {t}")
        for q in f["rotations"]:
            if len(q) != 4 or not all(math.isfinite(v) for v in q):
                errors.append(f"{label}: non-finite quaternion at {t}")
                continue
            n2 = sum(v * v for v in q)
            if abs(n2 - 1.0) > 1e-5:
                errors.append(f"{label}: non-unit quaternion at {t}")
            max_rot = max(max_rot, qangle(q, (0, 0, 0, 1)))
        for p in f["translations"]:
            if len(p) != 3 or not all(math.isfinite(v) for v in p):
                errors.append(f"{label}: non-finite translation at {t}")
        max_pelvis = max(max_pelvis, length(f["translations"][TARGET_PELVIS]))
    if abs(last_t - float(doc["duration"])) > 1e-5:
        errors.append(f"{label}: last frame does not match duration")
    if max_pelvis < 1e-3 and max_rot < 1e-3:
        errors.append(f"{label}: clip is static")
    return errors, {"frames": len(frames), "duration": last_t,
                    "max_pelvis_translation": max_pelvis,
                    "max_rotation_from_identity_deg": max_rot}


def check_basis(retargeter):
    errors = []
    if abs(mat_det(C_MATRIX) + 1.0) > 1.0e-12:
        errors.append(f"basis C determinant is {mat_det(C_MATRIX)}, expected -1 reflection")
    axes = [((0, -1, 0), (0, 0, -1), "forward"), ((-1, 0, 0), (1, 0, 0), "right"),
            ((0, 0, 1), (0, -1, 0), "up"), ((1, 0, 0), (-1, 0, 0), "source +X"),
            ((0, 1, 0), (0, 0, 1), "source +Y"), ((0, 0, -1), (0, 1, 0), "source -Z")]
    for src, expected, label in axes:
        got = cvec(src)
        if dist(got, expected) > 1e-9:
            errors.append(f"basis {label} {src} -> {got}, expected {expected}")

    # Prove the reflection has no unit-quaternion representation and that the vector-part
    # formula (x,z,-y) equals C R C^-1 for several rotations.
    rotations = [
        ("Rz90", euler_rz_ry_rx(0.0, 0.0, math.pi / 2.0)),
        ("Rx90", euler_rz_ry_rx(math.pi / 2.0, 0.0, 0.0)),
        ("Ry90", euler_rz_ry_rx(0.0, math.pi / 2.0, 0.0)),
        ("compound", euler_rz_ry_rx(0.3, -0.7, 1.1)),
    ]
    max_quat_matrix_deg = 0.0
    for name, q in rotations:
        mapped_q = c_rotate(q)
        expected_m = c_matrix(qmatrix(q))
        got_m = qmatrix(mapped_q)
        for i in range(3):
            for j in range(3):
                if abs(got_m[i][j] - expected_m[i][j]) > 1.0e-9:
                    errors.append(f"{name}: quaternion/matrix C conjugation disagrees")
                    break
        max_quat_matrix_deg = max(max_quat_matrix_deg,
                                  qangle(mapped_q, qfrommatrix(expected_m)))
        for v in ((0, -1, 0), (-1, 0, 0), (0, 0, 1),
                  (1, 0, 0), (0, 1, 0), (0, 0, -1), (0.4, -0.6, 0.8)):
            direct = cvec(qrot(q, v))
            via = qrot(mapped_q, cvec(v))
            if dist(direct, via) > 1.0e-9:
                errors.append(f"{name}: vector conversion disagrees for {v}")
    if max_quat_matrix_deg > 1.0e-5:
        errors.append(f"quaternion/matrix basis conversion off by {max_quat_matrix_deg} deg")

    # Neutral synthetic frame: torso/head stay identity relative the MDL bind because the
    # source neutral rotation delta is identity. Limb neutral quaternions are NOT forced to
    # identity: the fixed target bind links differ from the Source neutral links. Instead the
    # achieved target world limb directions and lengths must match corrected-C Source FK.
    rest_local = bind_local(retargeter.bind)
    neutral = retargeter.frame(rest_local)
    torso_head_angle = max(qangle(neutral["rotations"][i], (0, 0, 0, 1))
                           for i in (TARGET_PELVIS, TARGET_CHEST, TARGET_HEAD))
    max_pos = max(length(p) for p in neutral["translations"])
    limb = retargeter.frame_direction_report(retargeter.rest, neutral)
    max_limb_direction = 0.0
    max_tip_direction = 0.0
    max_limb_orientation = 0.0
    for side in ("right", "left"):
        max_limb_direction = max(max_limb_direction, limb[side]["upper"],
                                 limb[side]["lower"], limb["leg_" + side]["thigh"],
                                 limb["leg_" + side]["calf"])
        max_tip_direction = max(max_tip_direction, limb[side]["tip_direction"])
        max_limb_orientation = max(max_limb_orientation, limb[side]["hand_orientation"],
                                   limb["leg_" + side]["foot_orientation"])
    limb_joints = list(TARGET_WINGS[1]) + list(TARGET_WINGS[-1]) + \
        list(TARGET_LEGS[1]) + list(TARGET_LEGS[-1])
    max_limb_local = max(qangle(neutral["rotations"][i], (0, 0, 0, 1))
                         for i in limb_joints)
    if torso_head_angle > 1e-5:
        errors.append(f"neutral torso/head rotations not identity: {torso_head_angle} deg")
    if max_pos > 1e-5:
        errors.append(f"neutral synthetic translations not zero: {max_pos}")
    if max_limb_direction > 1e-4:
        errors.append(f"neutral limb world direction residual {max_limb_direction} deg")
    if max_tip_direction > 1e-4:
        errors.append(f"neutral visible tip direction residual {max_tip_direction} deg")
    if max_limb_orientation > 1e-4:
        errors.append(f"neutral limb tip orientation residual {max_limb_orientation} deg")
    return errors, {
        "c_determinant": mat_det(C_MATRIX),
        "max_quaternion_matrix_conversion_deg": max_quat_matrix_deg,
        "max_neutral_torso_head_rotation_deg": torso_head_angle,
        "max_neutral_translation": max_pos,
        "max_neutral_limb_direction_residual_deg": max_limb_direction,
        "max_neutral_visible_tip_direction_residual_deg": max_tip_direction,
        "max_neutral_limb_tip_orientation_residual_deg": max_limb_orientation,
        "max_neutral_limb_local_rotation_deg": max_limb_local,
        "neutral_limb_local_identity_required": False,
    }


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path, doc):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(doc, ensure_ascii=False, separators=(",", ":"))
    path.write_text(text + "\n", encoding="utf-8")


def compare_asset(label, checked_in, rebuilt, errors, limit=20):
    """Report exact JSON drift between a checked-in clip and its source rebuild."""
    diffs = []

    def walk(expected, actual, path):
        if len(diffs) >= limit:
            return
        if isinstance(expected, dict) and isinstance(actual, dict):
            if set(expected) != set(actual):
                diffs.append(f"{path}: keys {sorted(set(expected))} != {sorted(set(actual))}")
            for key in sorted(set(expected) & set(actual)):
                walk(expected[key], actual[key], f"{path}.{key}")
        elif isinstance(expected, list) and isinstance(actual, list):
            if len(expected) != len(actual):
                diffs.append(f"{path}: length {len(expected)} != {len(actual)}")
            for i, (a, b) in enumerate(zip(expected, actual)):
                walk(a, b, f"{path}[{i}]")
        elif expected != actual:
            diffs.append(f"{path}: checked-in {expected!r} != source {actual!r}")

    walk(checked_in, rebuilt, label)
    if diffs:
        errors.append(f"{label} asset drift from source rebuild: " + "; ".join(diffs))


def check_generated(source_dir, out_dir, report_path, rig):
    errors = []
    report = read_json(report_path)
    recomputed = build_scan(source_dir)
    # Recompute the full deterministic scan and compare every semantic field. The report's
    # export block and path strings depend on where the exporter was run, so exclude those.
    report_scan = {k: v for k, v in report.items() if k not in ("export", "source", "bind")}
    rebuilt_scan = {k: v for k, v in recomputed.items() if k not in ("export", "source", "bind")}
    if report_scan != rebuilt_scan:
        diffs = []
        for key in sorted(set(report_scan) | set(rebuilt_scan)):
            if report_scan.get(key) != rebuilt_scan.get(key):
                diffs.append(key)
        errors.append("scan report drift from deterministic scan: " + ", ".join(diffs))
    pole_doc = read_json(out_dir / "dance_pole.json")
    hip_doc = read_json(out_dir / "dance_hip.json")
    pole_err, pole_info = validate_clip(pole_doc, rig, "pole")
    hip_err, hip_info = validate_clip(hip_doc, rig, "hip")
    errors.extend(pole_err + hip_err)
    # Rebuild both clips from source and compare the complete documents, not just counts.
    retargeter = Retargeter(parse_bind(source_dir / POLE_BIND), rig)
    pole_rebuild, pole_seam = pole_build(source_dir, retargeter, rig)
    compare_asset("pole", pole_doc, pole_rebuild, errors)
    retargeter2 = Retargeter(parse_bind(source_dir / GETDOWN_BIND), rig)
    hip_rebuild, hip_seam = hip_build(source_dir, retargeter2, recomputed, rig)
    compare_asset("hip", hip_doc, hip_rebuild, errors)
    if pole_doc["source_sha256"] != sha256(source_dir / "animations" / POLE_SMD):
        errors.append("pole source hash mismatch")
    if hip_doc["source_sha256"] != sha256(source_dir / "animations" / GETDOWN_SMD):
        errors.append("hip source hash mismatch")
    if pole_doc["source_bind_sha256"] != sha256(source_dir / POLE_BIND):
        errors.append("pole bind hash mismatch")
    if hip_doc["source_bind_sha256"] != sha256(source_dir / GETDOWN_BIND):
        errors.append("hip bind hash mismatch")
    basis_err, basis_info = check_basis(retargeter2)
    errors.extend(basis_err)
    direction = {
        "pole": pole_doc.get("direction_validation"),
        "hip": hip_doc.get("direction_validation"),
    }
    for label, doc in (("pole", pole_doc), ("hip", hip_doc)):
        dv = doc.get("direction_validation")
        if not dv:
            errors.append(f"{label}: missing direction_validation")
            continue
        for key in ("max_wing_direction_residual_deg", "max_tip_direction_residual_deg",
                    "max_hand_orientation_residual_deg"):
            for side in ("right", "left"):
                if dv[key][side] > dv["tolerance_deg"]:
                    errors.append(f"{label}: {key} {side} = {dv[key][side]} deg")
        if dv["max_tip_offset_length_delta"] > 1.0e-9:
            errors.append(f"{label}: visible tip offset length changed")
    summary = {
        "ok": not errors,
        "errors": errors,
        "pole": pole_info,
        "hip": hip_info,
        "basis": basis_info,
        "direction": direction,
        "selected_range": recomputed["chosen"],
        "pole_seam": pole_seam,
        "hip_seam": hip_seam,
    }
    print(json.dumps(summary, indent=2))
    return 0 if not errors else 1


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE)
    ap.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    ap.add_argument("--check", action="store_true",
                    help="validate existing generated clips and source scan without writing")
    args = ap.parse_args(argv)
    source_dir = args.source_dir
    out_dir = args.out_dir
    rig = read_json(RIG_PATH)
    if args.check:
        return check_generated(source_dir, out_dir, args.report, rig)
    scan = build_scan(source_dir)
    pole_doc, pole_seam = pole_build(
        source_dir, Retargeter(parse_bind(source_dir / POLE_BIND), rig), rig)
    hip_doc, hip_seam = hip_build(
        source_dir, Retargeter(parse_bind(source_dir / GETDOWN_BIND), rig), scan, rig)
    for label, doc in (("pole", pole_doc), ("hip", hip_doc)):
        errors, _ = validate_clip(doc, rig, label)
        if errors:
            raise SystemExit("\n".join(errors))
    scan["export"] = {
        "pole_file": str(out_dir / "dance_pole.json"),
        "pole_source": str(source_dir / "animations" / POLE_SMD),
        "pole_exported_frames": len(pole_doc["frames"]),
        "pole_duration": pole_doc["duration"],
        "pole_runtime_rate": pole_doc["runtime_rate"],
        "pole_seam": pole_seam,
        "pole_direction_validation": pole_doc["direction_validation"],
        "hip_file": str(out_dir / "dance_hip.json"),
        "hip_source": str(source_dir / "animations" / GETDOWN_SMD),
        "hip_exported_frames": len(hip_doc["frames"]),
        "hip_duration": hip_doc["duration"],
        "hip_runtime_rate": hip_doc["runtime_rate"],
        "hip_seam": hip_seam,
        "hip_direction_validation": hip_doc["direction_validation"],
    }
    write_json(out_dir / "dance_pole.json", pole_doc)
    write_json(out_dir / "dance_hip.json", hip_doc)
    write_json(args.report, scan)
    print(json.dumps({
        "wrote": [str(out_dir / "dance_pole.json"), str(out_dir / "dance_hip.json"),
                  str(args.report)],
        "selected_range": scan["chosen"],
        "pole_frames": len(pole_doc["frames"]),
        "hip_frames": len(hip_doc["frames"]),
        "pole_direction_validation": pole_doc["direction_validation"],
        "hip_direction_validation": hip_doc["direction_validation"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
