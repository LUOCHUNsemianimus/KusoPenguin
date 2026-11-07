"""Export Ignis cuffs combined with the unmodified original fat body and connected feet.

Editable sleeve source is ring geometry, not borrowed Cataclysm assets; body/feet reuse HIGH.
Three-link closed flippers are baked into ADDITIONAL body triangles in a fixed belly-hug pose.
Positive-X flipper faces (268..303) bind rigidly to Ignis channel 5 and negative-X faces (304..339)
to channel 8, so idle geometry follows the torso while dance can rotate them at their belly-root
pivots without touching the native arm bones or the separate short cuffs (channels 4/7). --check
verifies byte reproducibility, predictable parts/faces and watertight, outward, non-degenerate
cuff topology.
"""
from pathlib import Path
from collections import Counter
import argparse
import json
import math
from ignis_belly_wings import build_wings, descriptor

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'models/prototypes/cataclysm/ignis_legless_source.json'
OUTPUT = ROOT / 'src/main/resources/assets/kuso_penguin/models/entity/cataclysm/ignis_legless.json'
HIGH = ROOT / 'src/main/resources/assets/kuso_penguin/models/entity/penguin_lowpoly.json'
BAKE_JOINT = 1
BARY_EPSILON = 1e-6










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




def build(data):
    if data['schema'] != 1 or type(data['sectors']) is not int or data['sectors'] < 6:
        raise ValueError('Invalid Ignis ring source')
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
    baked, segments, gap = build_wings(data, body['triangles'], front_z, unit_normal)
    body_with_wings = {'name': body['name'], 'pivot': body['pivot'],
                       'triangles': list(body['triangles']) + baked}
    return {'schema': 2, 'parts': [body_with_wings, cuffs['right_wing'], cuffs['left_wing'],
                                   retained['right_foot'], retained['left_foot']]}, segments, gap


def validate_counts(data):
    counts = {part['name']: len(part['triangles']) for part in data['parts']}
    expected = {'body': 268 + 6 * 12, 'right_wing': 72, 'left_wing': 72,
                'right_foot': 68, 'left_foot': 68}
    if counts != expected:
        raise ValueError('Unexpected Ignis part face counts: ' + repr(counts))
    faces = sum(counts.values())
    if faces != sum(expected.values()):
        raise ValueError('Unexpected Ignis source/baked vertex totals')
    return counts, faces


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data, segments, gap = build(json.loads(SOURCE.read_text(encoding='utf-8')))
    counts, faces = validate_counts(data)
    encoded = (json.dumps(data, separators=(',', ':'))+'\n').encode()
    contract = SOURCE.with_name('ignis_belly_wings.json')
    metadata = (json.dumps(descriptor(encoded, segments, gap), indent=2)+'\n').encode()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_bytes() != encoded:
            raise SystemExit('Ignis mesh drift; rerun exporter')
        if not contract.exists() or contract.read_bytes() != metadata:
            raise SystemExit('Ignis BBS descriptor drift; rerun exporter')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(encoded)
        contract.write_bytes(metadata)
    print('PASS Ignis original body/feet + closed cuffs + three-link belly-hug flippers',
          counts, {'source_faces': faces, 'baked_vertices': faces * 4})


if __name__ == '__main__':
    main()
