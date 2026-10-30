"""Canonical closed three-link belly flippers; runtime and BBS share face ownership."""
import hashlib
import math


def _unit(v):
    length = math.sqrt(sum(x*x for x in v))
    if not math.isfinite(length) or length < 1e-8:
        raise ValueError('Degenerate belly-wing direction')
    return [x/length for x in v]


def _cross(a, b):
    return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]


def build_wings(source, body_faces, front_z, normal):
    cfg = source['belly_wing_chain']
    controls = cfg['controls']
    if cfg.get('schema') != 1 or len(controls) != 4:
        raise ValueError('Expected four belly-wing controls')
    for c in controls:
        if len(c['center']) != 3 or not all(math.isfinite(x) for x in c['center']):
            raise ValueError('Invalid belly-wing center')
        if not (0 < c['width'] <= 2 and 0 < c['depth'] <= 1):
            raise ValueError('Invalid belly-wing cross-section')
    centers = [list(c['center']) for c in controls]
    if not (centers[0][1] < centers[1][1] and centers[1][0] > centers[2][0] > centers[3][0] > 0):
        raise ValueError('Upper wing must hang down; fore/tip must approach belly center')
    for center in centers[1:]:
        surface = front_z(body_faces, center[0], center[1])
        if surface is not None:
            center[2] = min(center[2], surface - .8)
    overlap = cfg['joint_overlap']
    if not 0 < overlap <= .4:
        raise ValueError('Invalid joint overlap')
    triangles, segments = [], []
    tips = []
    for side, mirror in [('right', 1), ('left', -1)]:
        parent = 'body'
        for index, name in enumerate(['upper', 'fore', 'tip']):
            a, b = centers[index], centers[index+1]
            axis = _unit([b[k]-a[k] for k in range(3)])
            width_axis = _unit(_cross([0, 0, -1], axis))
            depth_axis = _unit(_cross(axis, width_axis))
            rings = []
            for endpoint, center in enumerate([a, b]):
                c = controls[index+endpoint]
                extension = -overlap if endpoint == 0 and index else overlap if endpoint == 1 and index < 2 else 0
                origin = [center[k]+axis[k]*extension for k in range(3)]
                rings.append([[origin[k]+width_axis[k]*wx*c['width']+depth_axis[k]*dz*c['depth'] for k in range(3)]
                              for wx, dz in [(1, 0), (0, 1), (-1, 0), (0, -1)]])
            # A single translation preserves closed cross-sections and avoids flattening faces.
            penetration = 0.
            for ring in rings[(1 if index == 0 else 0):]:
                for p in ring:
                    surface = front_z(body_faces, p[0], p[1])
                    if surface is not None:
                        penetration = max(penetration, p[2] - surface + .15)
            for ring in rings:
                for p in ring:
                    p[2] -= penetration
            vertices = [[round(p[0]*mirror, 8), round(p[1], 8), round(p[2], 8)] for ring in rings for p in ring]
            faces = []
            for j in range(4):
                n = (j+1) % 4
                faces += [(j, n, n+4), (j, n+4, j+4)]
            faces += [(0, 2, 1), (0, 3, 2), (4, 5, 6), (4, 6, 7)]
            midpoint = [sum(p[k] for p in vertices)/8 for k in range(3)]
            first = len(body_faces)+len(triangles)
            for f in faces:
                p, q, r = [vertices[i] for i in f]
                n = normal(p, q, r)
                center = [(p[k]+q[k]+r[k])/3-midpoint[k] for k in range(3)]
                if sum(center[k]*n[k] for k in range(3)) < 0:
                    q, r = r, q
                    n = normal(p, q, r)
                triangles.append([list(v)+source['black_uv']+n+[1, 1, 0] for v in (p, q, r)])
            segment_id = 'belly_'+side+'_'+name
            segments.append({'id': segment_id, 'side': side, 'name': name, 'parent': parent,
                             'pivot': [a[0]*mirror, a[1], a[2]-penetration],
                             'body_triangles': list(range(first, len(body_faces)+len(triangles)))})
            parent = segment_id
            if index == 2:
                tips.extend(p[0] for p in vertices[4:])
    gap = 2*min(abs(x) for x in tips)
    if not cfg['tip_gap_min'] <= gap <= cfg['tip_gap_max'] or any(x <= 0 for x in tips[:4]):
        raise ValueError(f'Belly-wing tip gap outside bounds: {gap}')
    return triangles, segments, gap


def descriptor(encoded, segments, gap):
    return {'schema': 1, 'runtime_mesh_sha256': hashlib.sha256(encoded).hexdigest(),
            'body_base_triangle_count': 268, 'tip_gap': gap, 'segments': segments}
