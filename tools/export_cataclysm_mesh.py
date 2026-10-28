"""Export Ignis cuffs combined with the unmodified original fat body and connected feet.

Editable sleeve source is ring geometry, not borrowed Cataclysm assets; body/feet reuse HIGH.
--check verifies both reproducibility and watertight, outward, non-degenerate topology.
"""
from pathlib import Path
from collections import Counter
import argparse
import json
import math

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'models/prototypes/cataclysm/ignis_legless_source.json'
OUTPUT = ROOT / 'src/main/resources/assets/kuso_penguin/models/entity/cataclysm/ignis_legless.json'


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
    original = json.loads((ROOT / 'src/main/resources/assets/kuso_penguin/models/entity/penguin_lowpoly.json')
                          .read_text(encoding='utf-8'))
    retained = {p['name']: p for p in original['parts']}
    cuffs = {p['name']: p for p in parts if p['name'].endswith('_wing')}
    return {'schema': 2, 'parts': [retained['body'], cuffs['right_wing'], cuffs['left_wing'],
                                  retained['right_foot'], retained['left_foot']]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = build(json.loads(SOURCE.read_text(encoding='utf-8')))
    encoded = (json.dumps(data, separators=(',', ':'))+'\n').encode()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_bytes() != encoded:
            raise SystemExit('Ignis mesh drift; rerun exporter')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(encoded)
    print('PASS Ignis original body/feet + closed cuffs', {p['name']: len(p['triangles']) for p in data['parts']})


if __name__ == '__main__':
    main()
