package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import org.joml.Vector3f;

/** Resource-load topology/weight bake; only positions and flat normals change at draw time. */
public final class PenguinSkinnedMesh {
    private record Point(float x,float y,float z,int a,int b,float weight){}
    private record Face(int a,int b,int c,float u,float v){}
    private final Point[] points;
    private final Face[] faces;
    private final Vector3f[] transformed;
    private final Vector3f scratchA=new Vector3f(),scratchB=new Vector3f(),edge=new Vector3f(),normal=new Vector3f();
    private PenguinSkinnedMesh(Point[] points,Face[] faces){
        this.points=points;this.faces=faces;transformed=new Vector3f[points.length];
        for(int i=0;i<transformed.length;i++)transformed[i]=new Vector3f();
    }
    public int vertexCount(){return faces.length*4;}
    public static PenguinSkinnedMesh bake(PenguinMeshModel mesh,PenguinAnimationClip clip){
        Builder b=new Builder(clip);
        for(PenguinMeshModel.Part part:mesh.animationParts()){
            int joint=switch(part.name()){
                case "body"->PenguinRig.PELVIS;case "right_wing"->4;case "left_wing"->7;
                case "right_foot"->10;case "left_foot"->13;default->throw new IllegalArgumentException("Unknown mesh root");};
            float[] cuts;
            if(joint==PenguinRig.PELVIS)cuts=new float[0];
            else if(joint==4||joint==7){float y=clip.bind(joint+1).y,z=clip.bind(joint+2).y;cuts=new float[]{y-1,y,y+1,z-1,z,z+1};}
            else{float y=clip.bind(joint+1).y,z=clip.bind(joint+2).y;cuts=new float[]{y-.7f,y,y+.7f,z-.45f,z,z+.45f};}
            for(PenguinMeshModel.Triangle t:part.triangles()){
                if(t.a().u()!=t.b().u()||t.a().u()!=t.c().u()||t.a().v()!=t.b().v()||t.a().v()!=t.c().v())throw new IllegalArgumentException("Animated penguin requires one solid colour per face");
                List<List<Vector3f>> polygons=new ArrayList<>();
                polygons.add(List.of(position(t.a(),part),position(t.b(),part),position(t.c(),part)));
                for(float plane:cuts){
                    List<List<Vector3f>> next=new ArrayList<>();
                    for(List<Vector3f> poly:polygons){
                        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
                        for(Vector3f v:poly){min=Math.min(min,v.y);max=Math.max(max,v.y);}
                        if(min>=plane-.00001f||max<=plane+.00001f)next.add(poly);
                        else{List<Vector3f> upper=clip(poly,plane,true),lower=clip(poly,plane,false);if(upper.size()>=3)next.add(upper);if(lower.size()>=3)next.add(lower);}
                    }
                    polygons=next;
                }
                for(List<Vector3f> poly:polygons)for(int i=1;i+1<poly.size();i++)b.face(poly.get(0),poly.get(i),poly.get(i+1),joint,t.a().u(),t.a().v());
            }
        }
        return new PenguinSkinnedMesh(b.points.toArray(Point[]::new),b.faces.toArray(Face[]::new));
    }
    private static Vector3f position(PenguinMeshModel.Vertex v,PenguinMeshModel.Part p){return new Vector3f(v.x()+p.pivotX(),v.y()+p.pivotY(),v.z()+p.pivotZ());}
    private static List<Vector3f> clip(List<Vector3f> poly,float plane,boolean above){
        List<Vector3f> out=new ArrayList<>();
        for(int i=0;i<poly.size();i++){
            Vector3f a=poly.get(i),b=poly.get((i+1)%poly.size());boolean ai=above?a.y>=plane:a.y<=plane,bi=above?b.y>=plane:b.y<=plane;
            if(ai)out.add(a);
            if(ai!=bi){float t=(plane-a.y)/(b.y-a.y);Vector3f p=new Vector3f(a).lerp(b,t);p.y=plane;out.add(p);}
        }
        return out;
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static final class Builder {
        final PenguinAnimationClip clip;final List<Point> points=new ArrayList<>();final List<Face> faces=new ArrayList<>();final Map<Point,Integer> indices=new HashMap<>();
        Builder(PenguinAnimationClip clip){this.clip=clip;}
        int point(Vector3f p,int root){
            float x=Math.round(p.x*100000)/100000f,y=Math.round(p.y*100000)/100000f,z=Math.round(p.z*100000)/100000f;
            int a=root,b=root;float weight=0;
            if(root==PenguinRig.PELVIS){b=PenguinRig.CHEST;weight=smooth((17-y)/13);}
            else{
                float y1=clip.bind(root+1).y,y2=clip.bind(root+2).y;
                float radius=root==4||root==7?1:.7f,second=root==4||root==7?1:.45f;
                if(y<y1+radius){a=root;b=root+1;weight=smooth((y-y1+radius)/(2*radius));}
                else{a=root+1;b=root+2;weight=smooth((y-y2+second)/(2*second));}
            }
            Point key=new Point(x,y,z,a,b,weight);Integer old=indices.get(key);if(old!=null)return old;
            int i=points.size();points.add(key);indices.put(key,i);return i;
        }
        void face(Vector3f a,Vector3f b,Vector3f c,int root,float u,float v){
            if(new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).lengthSquared()<1e-10f)return;
            faces.add(new Face(point(a,root),point(b,root),point(c,root),u,v));
        }
    }
    
    /** Exact deformed body bound for death-floor placement, using the same baked skin weights. */
    float maxY(PenguinAnimationPose pose){
        float max=Float.NEGATIVE_INFINITY;
        Vector3f a=new Vector3f(),b=new Vector3f();
        for(Point p:points){
            a.set(p.x,p.y,p.z);b.set(a);pose.skin[p.a].transformPosition(a);pose.skin[p.b].transformPosition(b);
            max=Math.max(max,a.y+(b.y-a.y)*p.weight);
        }
        return max;
    }
    public void render(PoseStack stack,VertexConsumer buffer,int light,int overlay,int color,PenguinAnimationPose pose){
        for(int i=0;i<points.length;i++){
            Point p=points[i];scratchA.set(p.x,p.y,p.z);scratchB.set(scratchA);
            pose.skin[p.a].transformPosition(scratchA);pose.skin[p.b].transformPosition(scratchB);
            transformed[i].set(scratchA).lerp(scratchB,p.weight);
        }
        PoseStack.Pose matrix=stack.last();
        for(Face face:faces){
            Vector3f a=transformed[face.a],b=transformed[face.b],c=transformed[face.c];
            edge.set(b).sub(a);normal.set(c).sub(a);edge.cross(normal,normal);
            if(normal.lengthSquared()<1e-16f)throw new IllegalStateException("Animated penguin triangle collapsed");
            normal.normalize();
            vertex(buffer,matrix,a,face,light,overlay,color);vertex(buffer,matrix,b,face,light,overlay,color);
            vertex(buffer,matrix,c,face,light,overlay,color);vertex(buffer,matrix,c,face,light,overlay,color);
        }
    }
    private void vertex(VertexConsumer out,PoseStack.Pose pose,Vector3f p,Face f,int light,int overlay,int color){
        out.addVertex(pose,p.x/16,p.y/16,p.z/16).setColor(color).setUv(f.u,f.v)
                .setOverlay(overlay).setLight(light).setNormal(pose,normal.x,normal.y,normal.z);
    }
}
