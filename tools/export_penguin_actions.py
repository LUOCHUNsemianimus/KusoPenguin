"""Export the 16-joint flying kick from minimal COLLADA-derived references (stdlib only)."""
import argparse, json, math
from pathlib import Path

def dot(a,b): return sum(x*y for x,y in zip(a,b))
def unit(v):
    n=math.sqrt(dot(v,v))
    if n<1e-10: raise ValueError('Zero direction')
    return [x/n for x in v]
def cross(a,b): return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def mul(a,b):
    x,y,z,w=a;X,Y,Z,W=b
    return [w*X+x*W+y*Z-z*Y,w*Y-x*Z+y*W+z*X,w*Z+x*Y-y*X+z*W,w*W-x*X-y*Y-z*Z]
def inv(q): return [-q[0],-q[1],-q[2],q[3]]
def rotate(q,v): return mul(mul(q,v+[0]),inv(q))[:3]
def conv(v): return [-x for x in v]
def limit(q,degrees):
    q=unit(q)
    if q[3]<0:q=[-v for v in q]
    angle=2*math.acos(max(-1,min(1,q[3])));maximum=math.radians(degrees)
    if angle>maximum:q=[v*math.sin(maximum/2) for v in unit(q[:3])]+[math.cos(maximum/2)]
    return q
def aim(a,b):
    a=unit(a);b=unit(b);d=dot(a,b)
    if d<-.999999:return unit(cross(a,[1,0,0] if abs(a[0])<.9 else [0,0,1]))+[0]
    return unit(cross(a,b)+[1+d])
def direction(bones,start,end,parent):
    v=[b-a for a,b in zip(bones[start]['world_position_m'],bones[end]['world_position_m'])]
    return conv(rotate(inv(bones[parent]['world_rotation_xyzw']),unit(v)))

def export(root,check,action="flying_kick"):
    death=action=="flying_back_death"
    load=lambda p:json.loads((root/p).read_text(encoding='utf-8'))
    source=load('models/animation/'+action+'-source-reference.json')
    if death:
        source['frames']=source['frames'][:60]
        source['metadata']['duration_seconds']=source['frames'][-1]['time']
    neutral=load('models/animation/taunt-source-reference.json')['source']['frames'][0]['bones']
    joints=load('models/animation/penguin_rig.json')['joints'];bind=[j['bind'] for j in joints]
    pelvis0=neutral['mixamorig_Hips']['world_rotation_xyzw'];chest0=neutral['mixamorig_Spine2']['world_rotation_xyzw']
    head0=mul(neutral['mixamorig_Neck']['local_rotation_xyzw'],neutral['mixamorig_Head']['local_rotation_xyzw'])
    first_y=source['frames'][0]['bones']['Hips']['world_position_m'][1]
    frames=[]
    for f in source['frames']:
        b=f['bones'];q=[[0,0,0,1] for _ in joints];t=[[0,0,0] for _ in joints];s=[[1,1,1] for _ in joints]
        # No copied horizontal root travel: attack reach and collision remain vanilla.
        t[0][1]=0 # Actual collision-aware travel owns all jump height.
        if death:
            first=source['frames'][0]['bones']['Hips']['world_position_m']
            t[0]=[(first[i]-b['Hips']['world_position_m'][i])*12 for i in range(3)]
        pelvis=limit(mul(mul(inv(chest0),mul(b['Hips']['world_rotation_xyzw'],inv(pelvis0))),chest0),170 if death else 55)
        chest=limit(mul(inv(chest0),b['Spine2']['world_rotation_xyzw']),170 if death else 65)
        q[1]=pelvis;q[2]=mul(inv(pelvis),chest)
        head=mul(b['Neck']['local_rotation_xyzw'],b['Head']['local_rotation_xyzw'])
        q[3]=limit(mul(head,inv(head0)),55)
        for side,w,l in [('Right',4,10),('Left',7,13)]:
            upper=direction(b,side+'Arm',side+'ForeArm','Spine2')
            fore=direction(b,side+'ForeArm',side+'Hand','Spine2')
            hand=direction(b,side+'Hand',side+'HandMiddle1','Spine2')
            a=[v-u for u,v in zip(bind[w],bind[w+1])];c=[v-u for u,v in zip(bind[w+1],bind[w+2])]
            q[w]=limit(aim(a,upper),140)
            q[w+1]=limit(aim(c,rotate(inv(q[w]),fore)),110)
            q[w+2]=limit(aim([.4 if side=='Right' else -.4,3.5,.3],rotate(inv(mul(q[w],q[w+1])),hand)),65)
            thigh=direction(b,side+'UpLeg',side+'Leg','Hips')
            calf=direction(b,side+'Leg',side+'Foot','Hips')
            toes=direction(b,side+'Foot',side+'ToeBase','Hips')
            q[l]=limit(aim([0,1,0],thigh),150)
            q[l+1]=limit(aim([0,1,0],rotate(inv(q[l]),calf)),140)
            q[l+2]=limit(aim([0,0,-1],rotate(inv(mul(q[l],q[l+1])),toes)),80)
        frames.append({'time':f['time'],'rotations':q,'translations':t,'scales':s})
    result={'schema':1,'name':action,'source':'Mixamo Flying Kick; directional retarget, fixed target lengths, server-authorized physical travel',
            'source_sha256':source['metadata']['source_sha256'],'blend_in':.05 if death else .1,'blend_out':.05 if death else .2,
            'duration':source['metadata']['duration_seconds'],'joints':joints,'frames':frames}
    data=json.dumps(result,separators=(',',':'),allow_nan=False).encode('utf-8')
    path=root/('src/main/resources/assets/kuso_penguin/animations/'+action+'.json')
    if check:
        if path.read_bytes()!=data:raise ValueError('Export drift: '+str(path))
    else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    print(('CHECK OK' if check else 'EXPORTED')+' '+action+': '+str(len(frames))+' frames, '+str(result['duration'])+' seconds')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);p.add_argument('--check',action='store_true');a=p.parse_args();export(a.root,a.check);export(a.root,a.check,"flying_back_death")
