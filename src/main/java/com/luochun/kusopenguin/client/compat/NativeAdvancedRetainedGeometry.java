package com.luochun.kusopenguin.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;

/** Native terminal subtrees, with full Lionfish ancestor/visibility/scale semantics. */
final class NativeAdvancedRetainedGeometry {
    private final NativeAdvancedBoxRig.Path[] paths;
    private NativeAdvancedRetainedGeometry(NativeAdvancedBoxRig.Path[] paths) { this.paths=paths; }
    static NativeAdvancedRetainedGeometry resolve(EntityModel<?> model,String[]... names) {
        NativeAdvancedBoxRig.Path[] result=new NativeAdvancedBoxRig.Path[names.length];
        for(int i=0;i<names.length;i++) {
            result[i]=NativeAdvancedBoxRig.path(model,names[i]);
            if(result[i]==null) return null;
        }
        return new NativeAdvancedRetainedGeometry(result);
    }
    void render(PoseStack stack,VertexConsumer buffer,int light,int overlay,int color) {
        for(NativeAdvancedBoxRig.Path path:paths) path.render(stack,buffer,light,overlay,color);
    }
}
