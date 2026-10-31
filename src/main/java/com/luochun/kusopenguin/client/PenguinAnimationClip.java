package com.luochun.kusopenguin.client;

import com.google.gson.*;
import java.io.*;
import java.util.Arrays;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Validated immutable keyframes. Sampling never opens a resource or mutates the bind pose. */
public final class PenguinAnimationClip {
    private final float blendIn,blendOut,duration;
    private final Vector3f[] bind;
    private final float[] times;
    private final Quaternionf[][] rotations;
    private final Vector3f[][] translations,scales;
    private PenguinAnimationClip(float in,float out,float duration,Vector3f[] bind,float[] times,
             Quaternionf[][] rotations,Vector3f[][] translations,Vector3f[][] scales) {
        this.blendIn=in;this.blendOut=out;this.duration=duration;this.bind=bind;this.times=times;
        this.rotations=rotations;this.translations=translations;this.scales=scales;
    }
    public float totalDuration(){return blendIn+duration+blendOut;}
    public Vector3f bind(int i){return new Vector3f(bind[i]);}
    public record Sample(float weight,Quaternionf[] rotations,Vector3f[] translations,Vector3f[] scales) {}
    public Sample sample(float age) {
        if(!Float.isFinite(age))throw new IllegalArgumentException("Non-finite animation time");
        float weight=age<0||age>=totalDuration()?0:age<blendIn?smooth(age/blendIn):
                age>blendIn+duration?smooth((totalDuration()-age)/blendOut):1;
        float t=Math.max(0,Math.min(duration,age-blendIn));
        int found=Arrays.binarySearch(times,t),lo=found>=0?found:Math.max(0,-found-2);
        int hi=Math.min(times.length-1,lo+1);float f=lo==hi?0:(t-times[lo])/(times[hi]-times[lo]);
        Quaternionf[] q=new Quaternionf[PenguinRig.COUNT];Vector3f[] p=new Vector3f[q.length],s=new Vector3f[q.length];
        for(int i=0;i<q.length;i++){
            q[i]=new Quaternionf(rotations[lo][i]).slerp(rotations[hi][i],f).normalize();
            p[i]=new Vector3f(translations[lo][i]).lerp(translations[hi][i],f);
            s[i]=new Vector3f(scales[lo][i]).lerp(scales[hi][i],f);
        }
        return new Sample(weight,q,p,s);
    }
    public Sample sampleTerminal(float age){
        Sample s=sample(Math.min(age,blendIn+duration));
        return new Sample(age<blendIn?s.weight():1,s.rotations(),s.translations(),s.scales());
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    /** Every action must share exactly the same joint names, parents and bind pivots as Taunt. */
    public boolean sameRigAs(PenguinAnimationClip other){
        if(other==null||bind.length!=other.bind.length)return false;
        for(int i=0;i<bind.length;i++)if(bind[i].distanceSquared(other.bind[i])>1.0E-8F)return false;
        return true;
    }
    public static PenguinAnimationClip parse(Reader reader)throws IOException {
        try {
            JsonObject o=JsonParser.parseReader(reader).getAsJsonObject();
            if(number(o.get("schema"))!=1)throw new IOException("Animation schema must be 1");
            float in=number(o.get("blend_in")),out=number(o.get("blend_out")),duration=number(o.get("duration"));
            if(in<=0||out<=0||duration<=0||in+out+duration>30)throw new IOException("Invalid clip duration");
            JsonArray joints=o.getAsJsonArray("joints");if(joints.size()!=PenguinRig.COUNT)throw new IOException("Expected 16 joints");
            Vector3f[] bind=new Vector3f[PenguinRig.COUNT];
            for(int i=0;i<bind.length;i++){
                JsonObject j=joints.get(i).getAsJsonObject();
                if(!j.get("name").getAsString().equals(PenguinRig.NAMES[i])||number(j.get("parent"))!=PenguinRig.PARENTS[i])throw new IOException("Joint contract mismatch at "+i);
                bind[i]=vec(j.getAsJsonArray("bind"));
            }
            // The head anchor and fixed source part pivots remain the accepted model contract.
            if(bind[0].lengthSquared()!=0||bind[3].lengthSquared()!=0)throw new IOException("Root/head bind must be zero");
            float[][] expected={{5.45f,2.6f,.2f},{-5.45f,2.6f,.2f},{3.3f,15,0},{-3.3f,15,0}};
            int[] ids={4,7,10,13};for(int k=0;k<ids.length;k++)if(bind[ids[k]].distance(new Vector3f(expected[k]))>.0001f)throw new IOException("Bind shoulder/hip changed");
            JsonArray frames=o.getAsJsonArray("frames");if(frames.size()<2||frames.size()>2000)throw new IOException("Invalid frame count");
            int count=frames.size();float[] times=new float[count];Quaternionf[][] q=new Quaternionf[count][bind.length];
            Vector3f[][] p=new Vector3f[count][bind.length],s=new Vector3f[count][bind.length];
            for(int f=0;f<count;f++){
                JsonObject frame=frames.get(f).getAsJsonObject();times[f]=number(frame.get("time"));
                if(times[f]<0||(f>0&&times[f]<=times[f-1]))throw new IOException("Unordered animation time");
                JsonArray qs=frame.getAsJsonArray("rotations"),ps=frame.getAsJsonArray("translations"),ss=frame.getAsJsonArray("scales");
                if(qs.size()!=bind.length||ps.size()!=bind.length||ss.size()!=bind.length)throw new IOException("Joint sample count mismatch");
                for(int i=0;i<bind.length;i++){
                    JsonArray a=qs.get(i).getAsJsonArray();if(a.size()!=4)throw new IOException("Quaternion requires four values");
                    q[f][i]=new Quaternionf(number(a.get(0)),number(a.get(1)),number(a.get(2)),number(a.get(3)));
                    if(Math.abs(q[f][i].lengthSquared()-1)>.001f)throw new IOException("Quaternion must be unit length");q[f][i].normalize();
                    p[f][i]=vec(ps.get(i).getAsJsonArray());s[f][i]=vec(ss.get(i).getAsJsonArray());
                    if(p[f][i].length()>24||s[f][i].x<=0||s[f][i].y<=0||s[f][i].z<=0||Math.max(s[f][i].x,Math.max(s[f][i].y,s[f][i].z))>2)throw new IOException("Invalid translation or scale");
                }
            }
            if(times[0]!=0||Math.abs(times[count-1]-duration)>.0001f)throw new IOException("Clip endpoints do not match duration");
            return new PenguinAnimationClip(in,out,duration,bind,times,q,p,s);
        } catch(IOException e){throw e;}catch(RuntimeException e){throw new IOException("Malformed penguin animation",e);}
    }
    private static Vector3f vec(JsonArray a)throws IOException {
        if(a.size()!=3)throw new IOException("Vector requires three values");return new Vector3f(number(a.get(0)),number(a.get(1)),number(a.get(2)));
    }
    private static float number(JsonElement e)throws IOException {
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IOException("Expected finite number");
        float f=e.getAsFloat();if(!Float.isFinite(f))throw new IOException("Expected finite number");return f;
    }
}
