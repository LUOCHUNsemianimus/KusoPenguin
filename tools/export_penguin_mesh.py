"""Export the intentionally simple five-root BB prototypes as runtime triangle data.
Only unrotated direct-child meshes are accepted; bake transforms in BB first.
No model/texture writes with --check. Uses saved BB vertices, UVs and pivots with the native
Modded Entity convention: absolute (-BBx,24-BBy,BBz), local (px-vx,py-vy,vz-pz).
Select one tier with --level or export all three tiers (default), which share one texture.
When models/prototypes/penguin_leg_bindings.json exists it supplies authoritative per-foot-
vertex [jointA,jointB,weightB] bindings; only then is schema 2 written. Without it the schema 1
bytes remain identical to the legacy export.
"""
from pathlib import Path
import argparse,base64,json,math,sys
NAMES=['body','right_wing','left_wing','right_foot','left_foot']
FOOT_ROOTS={'right_foot','left_foot'}
PIVOTS=[[0,0,0],[5.45,2.6,.2],[-5.45,2.6,.2],[3.3,15,0],[-3.3,15,0]]
LEVELS={
 'high':('penguin_lowpoly','penguin_lowpoly.json'),
 'medium':('penguin_lowpoly_medium','penguin_lowpoly_medium.json'),
 'low':('penguin_lowpoly_low','penguin_lowpoly_low.json'),
}
def load_bindings(path):
 data=json.loads(path.read_text(encoding='utf-8'))
 if data.get('schema')!=1:raise ValueError('Binding sidecar schema must be 1')
 tiers=data.get('tiers')
 if not isinstance(tiers,dict) or set(tiers)!=set(LEVELS):raise ValueError('Binding sidecar must contain exactly the three tiers')
 for level in LEVELS:
  roots=tiers[level]
  if not isinstance(roots,dict) or set(roots)!=FOOT_ROOTS:raise ValueError('Binding sidecar tier '+level+' must contain exactly both foot roots')
  for root,vertices in roots.items():
   if not isinstance(vertices,dict):raise ValueError('Binding sidecar root '+root+' must be a vertex object')
   for vertex,binding in vertices.items():
    if not isinstance(vertex,str) or not isinstance(binding,list) or len(binding)!=3:
     raise ValueError('Binding sidecar entry '+root+'/'+str(vertex)+' must be [jointA,jointB,weightB]')
    a,b,w=binding
    if type(a) is not int or type(b) is not int or not 0<=a<16 or not 0<=b<16:
     raise ValueError('Binding sidecar joints must be integral 0..15: '+root+'/'+vertex)
    if type(w) not in (int,float) or not math.isfinite(w) or not 0<=w<=1:
     raise ValueError('Binding sidecar weight must be finite in [0,1]: '+root+'/'+vertex)
 return tiers
def _bindings_for_root(vertices,bindings,name):
 if bindings is None:return None
 root=bindings.get(name)
 if root is None:return None
 source_ids=set(vertices) if isinstance(vertices,dict) else {str(i) for i in range(len(vertices))}
 missing=source_ids-set(root);unknown=set(root)-source_ids
 if missing:raise ValueError('Binding sidecar misses '+name+' vertices: '+', '.join(sorted(missing)))
 if unknown:raise ValueError('Binding sidecar has unknown '+name+' vertices: '+', '.join(sorted(unknown)))
 return root
def export(source,bindings=None):
 b=json.loads(source.read_text(encoding='utf-8'));width=b['resolution']['width'];height=b['resolution']['height']
 groups={g['uuid']:g for g in b['groups']};elements={e['uuid']:e for e in b['elements']};parts={}
 for node in b['outliner']:
  g=groups[node['uuid']];name=g['name']
  if name not in NAMES or name in parts:raise ValueError('Expected five unique animation roots')
  if any(g.get('rotation',[0,0,0])):raise ValueError('Bake group rotation first: '+name)
  px,py,pz=g['origin'];pivot=[-px,24-py,pz]
  if any(abs(x-y)>1e-5 for x,y in zip(pivot,PIVOTS[NAMES.index(name)])):raise ValueError('Animation root pivot changed: '+name)
  p={'name':name,'pivot':pivot,'triangles':[]};parts[name]=p
  for child in node['children']:
   if not isinstance(child,str):raise ValueError('Bake child groups into direct meshes first')
   e=elements[child]
   if e['type']!='mesh' or any(e.get('rotation',[0,0,0])):raise ValueError('Only direct unrotated mesh elements supported')
   # BB mesh vertices already use model coordinates; origin is the rotation pivot.
   if not e.get('export',True):continue
   vertices=e['vertices'];named=isinstance(vertices,dict);root_bindings=_bindings_for_root(vertices,bindings,name)
   for f in e['faces'].values():
    ids=f['vertices']
    if len(ids)!=3:raise ValueError('Triangulate meshes in Blockbench before export')
    if f.get('texture')!=0:raise ValueError('Use the single embedded prototype atlas')
    # BB native Modded Entity mapping; X and Y are both reflected, so winding is unchanged.
    coords=[[px-vertices[k][0],py-vertices[k][1],vertices[k][2]-pz] for k in ids];uvs=[f['uv'][k] for k in ids]
    a=[coords[1][i]-coords[0][i] for i in range(3)];c=[coords[2][i]-coords[0][i] for i in range(3)]
    n=[a[1]*c[2]-a[2]*c[1],a[2]*c[0]-a[0]*c[2],a[0]*c[1]-a[1]*c[0]];length=math.sqrt(sum(v*v for v in n))
    if length<1e-8:raise ValueError('Degenerate triangle in '+name)
    n=[v/length for v in n];tri=[]
    for k,(pos,uv) in enumerate(zip(coords,uvs)):
     v=pos+[uv[0]/width,uv[1]/height]+n
     if not all(math.isfinite(x) for x in v):raise ValueError('Nonfinite vertex')
     if not all(0<=x<=1 for x in v[3:5]):raise ValueError('UV outside atlas')
     if root_bindings is not None:
      source_id=ids[k] if named else str(k)
      binding=root_bindings[source_id]
      v=v+[binding[0],binding[1],binding[2]]
     tri.append(v)
    p['triangles'].append(tri)
 if set(parts)!=set(NAMES):raise ValueError('Missing animation root')
 if len(elements)!=sum(len(n['children']) for n in b['outliner']):raise ValueError('Unassigned elements')
 source_texture=b['textures'][0]['source']
 if not source_texture.startswith('data:image/png;base64,'):raise ValueError('Embed the texture before saving BB')
 return {'schema':2 if bindings is not None else 1,'parts':[parts[n] for n in NAMES]},base64.b64decode(source_texture.split(',')[1])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');parser.add_argument('--level',choices=['high','medium','low','all'],default='all');parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);args=parser.parse_args()
 root=args.root
 # Optional sidecar is loaded once. It is never written by this exporter.
 binding_path=root/'models/prototypes/penguin_leg_bindings.json'
 bindings=load_bindings(binding_path) if binding_path.exists() else None
 # Always parse all three sources so a single-level write/check can never skip atlas consistency.
 exports=[]
 for level in LEVELS:
  model,texture=export(root/'models/prototypes'/(LEVELS[level][0]+'.bbmodel'),bindings[level] if bindings else None)
  exports.append((level,model,texture))
 shared_texture=exports[0][2]
 for level,_,texture in exports:
  if texture!=shared_texture:raise ValueError('Embedded texture differs between levels: '+level)
 selected=exports if args.level=='all' else [entry for entry in exports if entry[0]==args.level]
 model_dir=root/'src/main/resources/assets/kuso_penguin/models/entity'
 targets=[(model_dir/LEVELS[level][1],json.dumps(model,separators=(',',':')).encode()) for level,model,_ in selected]
 targets.append((root/'src/main/resources/assets/kuso_penguin/textures/entity/penguin_lowpoly.png',shared_texture))
 for path,data in targets:
  if args.check:
   if not path.exists() or path.read_bytes()!=data:raise ValueError('Export drift: '+str(path))
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 counts=', '.join(level+'='+str(sum(len(p['triangles']) for p in model['parts'])) for level,model,_ in selected)
 print(('CHECK OK' if args.check else 'EXPORTED')+' tiers '+counts+' triangles; mesh/UV/texture from saved BB, shared texture consistent across all three sources')
if __name__=='__main__':
 try:main()
 except (KeyError,ValueError,OSError) as e:print('ERROR: '+str(e),file=sys.stderr);sys.exit(1)
