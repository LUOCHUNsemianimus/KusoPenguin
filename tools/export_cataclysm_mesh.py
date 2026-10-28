"""Export Ignis cuffs combined with the unmodified original fat body and connected feet.

Editable sleeve source is ring geometry, not borrowed Cataclysm assets; body/feet reuse HIGH.
The original HIGH flippers are baked into ADDITIONAL body triangles in a fixed belly-hug pose.
They bind rigidly to body joint 1, so they follow the torso without touching the native arm
bones or the separate short cuffs (joints 4/7). --check verifies byte reproducibility,
predictable parts/faces and watertight, outward, non-degenerate cuff topology.
"""
from pathlib import Path
from collections import Counter
import argparse
import json
import math

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'models/prototypes/cataclysm/ignis_legless_source.json'
OUTPUT = ROOT / 'src/main/resources/assets/kuso_penguin/models/entity/cataclysm/ignis_legless.json'
HIGH = ROOT / 'src/main/resources/assets/kuso_penguin/models/entity/penguin_lowpoly.json'
BAKE_JOINT = 1
BARY_EPSILON = 1e-6


def _finite_number(value, label, minimum=None, maximum=None):
    if type(value) not in (int, float) or not math.isfinite(value):
        raise ValueError('Invalid fixed belly wing parameter: ' + label)
    if minimum is not None and value < minimum:
        raise ValueError('Invalid fixed belly wing parameter: ' + label)
    if maximum is not None and value > maximum:
        raise ValueError('Invalid fixed belly wing parameter: ' + label)
    return float(value)


def wing_config(data):
    """Read the editable fixed-pose parameters instead of hardcoding the hug in code."""
    config = data.get('fixed_belly_wings')
    if not isinstance(config, dict):
        raise ValueError('Missing fixed_belly_wings source parameters')
    inward = _finite_number(config.get('inward_degrees'), 'inward_degrees', 0, 90)
    forward = _finite_number(config.get('forward_degrees'), 'forward_degrees', 0, 90)
    low = _finite_number(config.get('clearance_low'), 'clearance_low', 0)
    high = _finite_number(config.get('clearance_high'), 'clearance_high', 0)
    y_low = _finite_number(config.get('clearance_y_low'), 'clearance_y_low')
    y_high = _finite_number(config.get('clearance_y_high'), 'clearance_y_high')
    center = _finite_number(config.get('thickness_center_z'), 'thickness_center_z')
    scale = _finite_number(config.get('thickness_scale'), 'thickness_scale', 0, 2)
    if inward <= 0 or forward <= 0:
        raise ValueError('Hug angles must be positive')
    if high < low or y_high <= y_low:
        raise ValueError('Invalid clearance ramp')
    return {
        'inward': math.radians(inward),
        'forward': math.radians(forward),
        'low': low,
        'high': high,
        'y_low': y_low,
        'y_high': y_high,
        'center': center,
        'scale': scale,
    }


def rotate_z(point, angle):
    x, y, z = point
    cosine, sine = math.cos(angle), math.sin(angle)
    return [x * cosine - y * sine, x * sine + y * cosine, z]


def rotate_x(point, angle):
    x, y, z = point
    cosine, sine = math.cos(angle), math.sin(angle)
    return [x, y * cosine - z * sine, y * sine + z * cosine]


def front_z(body_faces, x, y):
    """Frontmost original body Z at (x, y) from non-degenerate XY-projected triangles."""
    front = None
    for triangle in body_faces:
        (x1, y1, z1), (x2, y2, z2), (x3, y3, z3) = (v[:3] for v in triangle)
        if not all(math.isfinite(v) for v in (x1, y1, z1, x2, y2, z2, x3, y3, z3)):
            raise ValueError('Non-finite body envelope triangle')
        denominator = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3)
        if abs(denominator) < 1e-12:
            continue
        a = ((y2 - y3) * (x - x3) + (x3 - x2) * (y - y3)) / denominator
        b = ((y3 - y1) * (x - x3) + (x1 - x3) * (y - y3)) / denominator
        c = 1.0 - a - b
        if a < -BARY_EPSILON or b < -BARY_EPSILON or c < -BARY_EPSILON:
            continue
        z = a * z1 + b * z2 + c * z3
        if front is None or z < front:
            front = z
    return front


def clearance(original_y, config):
    if original_y <= config['y_low']:
        return config['low']
    if original_y >= config['y_high']:
        return config['high']
    ratio = (original_y - config['y_low']) / (config['y_high'] - config['y_low'])
    return config['low'] + (config['high'] - config['low']) * ratio


def unit_normal(a, b, c):
    ab = [b[i] - a[i] for i in range(3)]
    ac = [c[i] - a[i] for i in range(3)]
    normal = [ab[1] * ac[2] - ab[2] * ac[1],
              ab[2] * ac[0] - ab[0] * ac[2],
              ab[0] * ac[1] - ab[1] * ac[0]]
    length = math.sqrt(sum(value * value for value in normal))
    if not math.isfinite(length) or length < 1e-8:
        raise ValueError('Degenerate posed wing triangle')
    return [value / length for value in normal]


def baked_wing(part, angle, config, body_faces):
    """Pose the original flipper, wrap it onto the original body front, then append to body."""
    pivot = part['pivot']
    triangles = part['triangles']
    if len(triangles) != 20:
        raise ValueError('Original HIGH ' + part['name'] + ' must have 20 triangles')
    posed = {}
    cache = {}
    result = []
    for triangle in triangles:
        face_uv = triangle[0][3:5]
        vertices = []
        for vertex in triangle:
            if len(vertex) not in (8, 11) or not all(math.isfinite(v) for v in vertex[:3]):
                raise ValueError('Unexpected original wing vertex format')
            global_point = [vertex[i] + pivot[i] for i in range(3)]
            # Keep distinct front/back thickness vertices even when their X/Y coincide.
            key = (round(global_point[0], 6), round(global_point[1], 6),
                   round(global_point[2], 6))
            if key not in posed:
                # Global -> shoulder-relative -> inward Z then forward X -> global.
                relative = [global_point[i] - pivot[i] for i in range(3)]
                relative = rotate_z(relative, angle)
                relative = rotate_x(relative, -config['forward'])
                x, y, z = (round(relative[i] + pivot[i], 6) for i in range(3))
                surface = cache.get((x, y), 'unset')
                if surface == 'unset':
                    surface = front_z(body_faces, x, y)
                    cache[(x, y)] = surface
                if surface is None:
                    shaped_z = z
                else:
                    shaped_z = round(surface - clearance(global_point[1], config)
                                     + (global_point[2] - config['center']) * config['scale'], 6)
                posed[key] = [x, y, shaped_z]
            vertices.append(posed[key])
        a, b, c = vertices
        normal = unit_normal(a, b, c)
        result.append([list(a) + list(face_uv) + normal + [BAKE_JOINT, BAKE_JOINT, 0],
                       list(b) + list(face_uv) + normal + [BAKE_JOINT, BAKE_JOINT, 0],
                       list(c) + list(face_uv) + normal + [BAKE_JOINT, BAKE_JOINT, 0]])
    return result


def build(data):
    if data['schema'] != 1 or type(data['sectors']) is not int or data['sectors'] < 6:
        raise ValueError('Invalid Ignis ring source')
    config = wing_config(data)
    count = data['sectors']
    parts = []
    for name, pivot, rings, joint in [
        ('right_wing', [5.45, 2.6, .2], data['sleeve_rings'], 4),
        ('left_wing', [-5.45, 2.6, .2], data['sleeve_rings'], 7),
    ]:
        if len(rings) < 2 or any(len(r) != 3 or r[1] <= 0 or r[2] <= 0 for r in rings):
            raise ValueError('Invalid ring dimensions')
        if any(a[0] >= b[0] for a, b in zip(rings, rings[1:])):
            raise ValueError('Ring heights must increase')
        vertices = [[(round(rx * math.cos(i * 2 * math.pi / count), 6), y,
                      round(rz * math.sin(i * 2 * math.pi / count), 6))
                     for i in range(count)] for y, rx, rz in rings]
        faces = []
        for a, b in zip(vertices, vertices[1:]):
            for i in range(count):
                j = (i + 1) % count
                faces.extend([(a[i], b[i], b[j]), (a[i], b[j], a[j])])
        for ring, reverse in [(vertices[0], True), (vertices[-1], False)]:
            center = (0, ring[0][1], 0)
            for i in range(count):
                a, b = ring[i], ring[(i + 1) % count]
                faces.append((center, a, b) if reverse else (center, b, a))
        triangles = []
        edges = Counter()
        volume = 0
        for a, b, c in faces:
            ab = [b[i] - a[i] for i in range(3)]
            ac = [c[i] - a[i] for i in range(3)]
            n = [ab[1]*ac[2]-ab[2]*ac[1], ab[2]*ac[0]-ab[0]*ac[2], ab[0]*ac[1]-ab[1]*ac[0]]
            length = math.sqrt(sum(x*x for x in n))
            if length < 1e-8:
                raise ValueError('Degenerate face')
            normal = [x/length for x in n]
            uv = data['black_uv']
            triangles.append([list(v)+uv+normal+[joint,joint,0] for v in (a,b,c)])
            for start, end in [(a,b),(b,c),(c,a)]:
                edges[tuple(sorted((start,end)))] += 1
            volume += sum(a[i]*n[i] for i in range(3))/6
        if any(v != 2 for v in edges.values()) or volume <= 0:
            raise ValueError('Mesh must be closed and outward')
        parts.append({'name': name, 'pivot': pivot, 'triangles': triangles})
    # User-approved fat body and connected legs are reused byte-for-value, including the real
    # black/cream face boundaries and explicit leg weights. Only the old cuffs remain custom.
    original = json.loads(HIGH.read_text(encoding='utf-8'))
    retained = {p['name']: p for p in original['parts']}
    cuffs = {p['name']: p for p in parts if p['name'].endswith('_wing')}
    # The fixed hug pose is baked into extra body faces before any cuff/feet part is emitted.
    # The original 268 faces keep their exact vertex order, UVs, colours and weights.
    body = retained['body']
    baked = []
    for name, angle in (('right_wing', config['inward']), ('left_wing', -config['inward'])):
        baked.extend(baked_wing(retained[name], angle, config, body['triangles']))
    body_with_wings = {'name': body['name'], 'pivot': body['pivot'],
                       'triangles': list(body['triangles']) + baked}
    return {'schema': 2, 'parts': [body_with_wings, cuffs['right_wing'], cuffs['left_wing'],
                                   retained['right_foot'], retained['left_foot']]}


def validate_counts(data):
    counts = {part['name']: len(part['triangles']) for part in data['parts']}
    expected = {'body': 308, 'right_wing': 72, 'left_wing': 72,
                'right_foot': 68, 'left_foot': 68}
    if counts != expected:
        raise ValueError('Unexpected Ignis part face counts: ' + repr(counts))
    faces = sum(counts.values())
    if faces != 588 or faces * 4 != 2352:
        raise ValueError('Unexpected Ignis source/baked vertex totals')
    return counts, faces


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = build(json.loads(SOURCE.read_text(encoding='utf-8')))
    counts, faces = validate_counts(data)
    encoded = (json.dumps(data, separators=(',', ':'))+'\n').encode()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_bytes() != encoded:
            raise SystemExit('Ignis mesh drift; rerun exporter')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(encoded)
    print('PASS Ignis original body/feet + closed cuffs + fixed belly-hug flippers',
          counts, {'source_faces': faces, 'baked_vertices': faces * 4})


if __name__ == '__main__':
    main()
