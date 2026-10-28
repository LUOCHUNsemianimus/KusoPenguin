"""Derive an independent solid-face penguin shell with an actual upper-front head opening.

The front of the upper torso is clipped away; no transparent mask, moved native face or altered
shared model is involved. Existing lower body, wings and connected leg weights remain intact.
"""
from pathlib import Path
import argparse
import json

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'models/prototypes/cataclysm/ender_guardian_source.json'
BASE=ROOT/'src/main/resources/assets/kuso_penguin/models/entity/penguin_lowpoly.json'
OUTPUT=ROOT/'src/main/resources/assets/kuso_penguin/models/entity/cataclysm/ender_guardian.json'


def clip(poly,axis,bound,above):
    out=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        ia=a[axis]>=bound if above else a[axis]<=bound
        ib=b[axis]>=bound if above else b[axis]<=bound
        if ia:out.append(a)
        if ia!=ib:
            t=(bound-a[axis])/(b[axis]-a[axis])
            v=[x+(y-x)*t for x,y in zip(a,b)]
            v[axis]=bound
            out.append(v)
    return out


def area2(t):
    a,b,c=t;u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
    n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
    return sum(x*x for x in n)


def build():
    cfg=json.loads(SOURCE.read_text(encoding='utf-8'))
    if cfg['schema']!=1 or not 0<cfg['notch_y']<12 or not -4<cfg['notch_z']<4:
        raise ValueError('Invalid head-opening parameters')
    data=json.loads(BASE.read_text(encoding='utf-8'))
    body=next(p for p in data['parts'] if p['name']=='body')
    out=[]
    for tri in body['triangles']:
        # Partition first, so the two retained regions never overlap or duplicate triangles.
        lower=clip(tri,1,cfg['notch_y'],True)
        upper=clip(tri,1,cfg['notch_y'],False)
        back=clip(upper,2,cfg['notch_z'],True) if upper else []
        for poly in (lower,back):
            for i in range(1,len(poly)-1):
                face=[poly[0],poly[i],poly[i+1]]
                if area2(face)<1e-10:continue
                if any(v[3:5]!=face[0][3:5] for v in face):
                    raise ValueError('Each new face must retain one solid colour')
                out.append(face)
    if not out or len(out)==len(body['triangles']):
        raise ValueError('Head opening was not generated')
    body['triangles']=out
    return data


def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    data=build();encoded=(json.dumps(data,separators=(',',':'))+'\n').encode()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_bytes()!=encoded:
            raise SystemExit('Guardian mesh drift; rerun exporter')
    else:
        OUTPUT.parent.mkdir(parents=True,exist_ok=True);OUTPUT.write_bytes(encoded)
    print('PASS independent Guardian head opening', {p['name']:len(p['triangles']) for p in data['parts']})


if __name__=='__main__':main()
