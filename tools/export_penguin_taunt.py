"""Rebuild the checked-in taunt from the minimal Mixamo source reference, standard library only."""
from pathlib import Path
import json,math,argparse
class M:
 @staticmethod
 def dot(a,b):return sum(x*y for x,y in zip(a,b))
 @staticmethod
 def unit(v):
  n=math.sqrt(M.dot(v,v))
  if n<1e-12:raise ValueError('Zero vector')
  return [x/n for x in v]
 @staticmethod
 def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
 @staticmethod
 def qinv(q):return [-q[0],-q[1],-q[2],q[3]]
 @staticmethod
 def qmul(a,b):
  x,y,z,w=a;X,Y,Z,W=b
  return [w*X+x*W+y*Z-z*Y,w*Y-x*Z+y*W+z*X,w*Z+x*Y-y*X+z*W,w*W-x*X-y*Y-z*Z]
m=M
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);args=parser.parse_args();stage=args.root
reference=json.loads((stage/'models/animation/taunt-source-reference.json').read_text(encoding='utf-8'));source=reference['source'];directions=reference['directions']
names=['root','pelvis','chest','head_mount','right_wing','right_wing_mid','right_wing_tip','left_wing','left_wing_mid','left_wing_tip','right_hip','right_knee','right_foot','left_hip','left_knee','left_foot'];parents=[-1,0,1,2,2,4,5,2,7,8,0,10,11,0,13,14]
bind=[[0,0,0],[0,16,0],[0,12,0],[0,0,0],[5.45,2.6,.2],[6.853177,6.5,.358546],[8.018627,10,.526471],[-5.45,2.6,.2],[-6.853177,6.5,.358546],[-8.018627,10,.526471],[3.3,15,0],[3.3,20.5,0],[3.3,22.3,0],[-3.3,15,0],[-3.3,20.5,0],[-3.3,22.3,0]]
def convq(q):return q[:]  # C=-I: C*R*C^-1 = R. Directions invert all axes.
def convv(v):return [-v[0],-v[1],-v[2]]
def rotate(q,v):return m.qmul(m.qmul(q,v+[0]),m.qinv(q))[:3]
def limit(q,degrees):
 q=m.unit(q)
 if q[3]<0:q=[-x for x in q]
 ang=2*math.acos(max(-1,min(1,q[3])));maxa=math.radians(degrees)
 if ang>maxa:
  axis=m.unit(q[:3]);q=[x*math.sin(maxa/2) for x in axis]+[math.cos(maxa/2)]
 return q
def aim(a,b):
 a=m.unit(a);b=m.unit(b);d=m.dot(a,b)
 if d<-.999999:
  axis=m.unit(m.cross(a,[1,0,0] if abs(a[0])<.9 else [0,0,1]));return axis+[0]
 return m.unit(m.cross(a,b)+[1+d])
first=source['frames'][0]['bones'];reference={}
for n in ['mixamorig_Hips','mixamorig_Spine2']:reference[n]=first[n]['world_rotation_xyzw']
reference['head']=m.qmul(first['mixamorig_Neck']['local_rotation_xyzw'],first['mixamorig_Head']['local_rotation_xyzw'])
frames=[]
for i,f in enumerate(source['frames']):
 q=[[0,0,0,1] for _ in names];scales=[[1,1,1] for _ in names]
 hip=f['bones']['mixamorig_Hips']['world_rotation_xyzw'];ch=f['bones']['mixamorig_Spine2']['world_rotation_xyzw'];pq=limit(convq(m.qmul(m.qmul(m.qinv(reference['mixamorig_Spine2']),m.qmul(hip,m.qinv(reference['mixamorig_Hips']))),reference['mixamorig_Spine2'])),9);cq=limit(convq(m.qmul(m.qinv(reference['mixamorig_Spine2']),ch)),22);q[1]=pq;q[2]=m.qmul(m.qinv(pq),cq)
 h=m.qmul(f['bones']['mixamorig_Neck']['local_rotation_xyzw'],f['bones']['mixamorig_Head']['local_rotation_xyzw']);q[3]=limit(convq(m.qmul(h,m.qinv(reference['head']))),38)
 scales[2]=[1,1+.04*math.sin(math.pi*f['time_seconds']/2.833333)**2,1]
 for side,offset in [('right',4),('left',7)]:
  d=directions[side][i];upper=convv(d['upper_direction_in_spine2_frame']);fore=convv(d['forearm_direction_in_spine2_frame']);b0,b1,b2=[bind[offset+k] for k in range(3)];v01=m.unit([b-a for a,b in zip(b0,b1)]);v12=m.unit([b-a for a,b in zip(b1,b2)]);vtip=m.unit([(.4 if side=='right' else -.4),3.5,.3]);q[offset]=limit(aim(v01,upper),115);q[offset+1]=limit(aim(v12,rotate(m.qinv(q[offset]),fore)),70)
  hand=f['bones']['mixamorig_'+side.title()+'Hand']['world_position_m'];middle=f['bones']['mixamorig_'+side.title()+'HandMiddle1']['world_position_m'];hv=m.unit([b-a for a,b in zip(hand,middle)]);chestq=f['bones']['mixamorig_Spine2']['world_rotation_xyzw'];hv=convv(rotate(m.qinv(chestq),hv));parent=m.qmul(q[offset],q[offset+1]);q[offset+2]=limit(aim(vtip,rotate(m.qinv(parent),hv)),30)
 frames.append({'time':f['time_seconds'],'rotations':q,'translations':[[0,0,0] for _ in names],'scales':scales})
asset={'schema':1,'name':'taunt','source':'Mixamo Standing Taunt Battlecry, directional retarget; no copied source bone lengths/root translation','source_sha256':source['metadata']['source_sha256'],'blend_in':.25,'blend_out':.35,'duration':2.833333,'joints':[{'name':n,'parent':p,'bind':b} for n,p,b in zip(names,parents,bind)],'frames':frames}
def put(path,obj):
 p=stage/path;p.parent.mkdir(parents=True,exist_ok=True);data=json.dumps(obj,separators=(',',':')).encode('utf-8')
 if args.check:
  if not p.exists() or p.read_bytes()!=data:raise ValueError('Export drift: '+str(p))
 else:p.write_bytes(data)
put('src/main/resources/assets/kuso_penguin/animations/taunt.json',asset)
put('models/animation/penguin_rig.json',{'joints':asset['joints'],'note':'Java model-pixel bind pivots. Leg roots are separately planted for taunt; hip/knee/foot chains can drive future kicks. Runtime weights are baked from immutable mesh positions by PenguinSkinnedMesh.'})
print(('CHECK OK' if args.check else 'EXPORTED')+' taunt: 86 frames, 16 joints')
