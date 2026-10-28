package com.luochun.kusopenguin.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.client.model.EntityModel;
import org.joml.Matrix4f;

/** Read-only Lionfish 3.1 transform adapter, with no optional API in a class descriptor. */
final class NativeAdvancedBoxRig {
    private NativeAdvancedBoxRig() {}

    // Metadata only: every animated transform is read again for the current actor/pass.
    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        @Override protected Optional<Access> computeValue(Class<?> type) {
            if (!type.getName().equals(
                    "com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox")) {
                return Optional.empty();
            }
            try { return Optional.of(new Access(type)); }
            catch (NoSuchFieldException | NoSuchMethodException e) { return Optional.empty(); }
        }
    };

    static Path path(EntityModel<?> model, String... fields) {
        Object[] nodes = new Object[fields.length];
        Access access = null;
        try {
            for (int i = 0; i < fields.length; i++) {
                Field field = model.getClass().getDeclaredField(fields[i]);
                field.setAccessible(true);
                nodes[i] = field.get(model);
                if (nodes[i] == null) return null;
                Access found = ACCESS.get(nodes[i].getClass()).orElse(null);
                if (found == null || (access != null && access != found)) return null;
                access = found;
                if (i > 0 && !((java.util.List<?>) access.children.get(nodes[i - 1]))
                        .contains(nodes[i])) return null;
            }
        } catch (NoSuchFieldException e) {
            return null; // Different optional model layout: keep its complete native draw.
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot inspect native model fields", e);
        }
        return nodes.length == 0 ? null : new Path(nodes, access);
    }

    static final class Path {
        final Object[] nodes;
        final Access access;
        Path(Object[] nodes, Access access) { this.nodes = nodes; this.access = access; }

        boolean visible() {
            for (Object node : nodes) if (!access.flag(access.show, node)) return false;
            return true;
        }

        boolean usableScale() {
            for (Object node : nodes) for (Field scale : access.scale) {
                float s = access.number(scale, node);
                if (!Float.isFinite(s) || s < .01F) return false;
            }
            return true;
        }

        boolean rest(float x, float y, float z, float rx, float ry, float rz) {
            float[] expected = {x,y,z,rx,ry,rz,1,1,1};
            Object end = nodes[nodes.length - 1];
            for (int i=0;i<expected.length;i++) {
                if (!(Math.abs(access.number(access.defaults[i],end)-expected[i])<=1e-4F)) {
                    return false;
                }
            }
            return true;
        }

        Matrix4f current() { return matrix(false); }
        Matrix4f initial() { return matrix(true); }
        private Matrix4f matrix(boolean initial) {
            Matrix4f out = new Matrix4f();
            for (int i=0;i<nodes.length;i++) {
                Object node=nodes[i];
                Field[] f=initial?access.defaults:access.current;
                out.translate(access.number(f[0],node),access.number(f[1],node),
                                access.number(f[2],node))
                        .rotateZ(access.number(f[5],node)).rotateY(access.number(f[4],node))
                        .rotateX(access.number(f[3],node))
                        .scale(access.number(f[6],node),access.number(f[7],node),
                                access.number(f[8],node));
                if (i+1<nodes.length && !access.flag(access.scaleChildren,node)) {
                    // Lionfish scales the node's cubes, then cancels that scale before children.
                    out.scale(1/Math.max(access.number(f[6],node),1e-4F),
                            1/Math.max(access.number(f[7],node),1e-4F),
                            1/Math.max(access.number(f[8],node),1e-4F));
                }
            }
            // Pixels throughout; native render methods alone convert translations to blocks.
            // offsetXYZ/isHidden are not consumed by Lionfish3.1 render; do not invent semantics.
            return out;
        }

        void render(PoseStack stack, VertexConsumer buffer, int light, int overlay, int color) {
            if (!visible()) return;
            stack.pushPose();
            try {
                for (int i=0;i+1<nodes.length;i++) {
                    Object node=nodes[i];
                    access.invoke(access.transform,node,stack);
                    if (!access.flag(access.scaleChildren,node)) {
                        stack.scale(1/Math.max(access.number(access.scale[0],node),1e-4F),
                                1/Math.max(access.number(access.scale[1],node),1e-4F),
                                1/Math.max(access.number(access.scale[2],node),1e-4F));
                    }
                }
                access.invoke(access.render,nodes[nodes.length-1],stack,buffer,light,overlay,color);
            } finally { stack.popPose(); }
        }
    }

    static final class Access {
        final Field[] current, defaults, scale;
        final Field show, scaleChildren, children;
        final Method transform, render;
        Access(Class<?> type) throws NoSuchFieldException, NoSuchMethodException {
            current=fields(type,"rotationPointX","rotationPointY","rotationPointZ",
                    "rotateAngleX","rotateAngleY","rotateAngleZ","xScale","yScale","zScale");
            defaults=fields(type,"defaultPositionX","defaultPositionY","defaultPositionZ",
                    "defaultRotationX","defaultRotationY","defaultRotationZ",
                    "defaultscaleX","defaultscaleY","defaultscaleZ");
            scale=new Field[]{current[6],current[7],current[8]};
            show=type.getField("showModel"); scaleChildren=type.getField("scaleChildren");
            children=type.getField("childModels");
            transform=type.getMethod("translateAndRotate",PoseStack.class);
            render=type.getMethod("render",PoseStack.class,VertexConsumer.class,
                    int.class,int.class,int.class);
        }
        private static Field[] fields(Class<?> type,String... names) throws NoSuchFieldException {
            Field[] result=new Field[names.length];
            for(int i=0;i<names.length;i++) result[i]=type.getField(names[i]);
            return result;
        }
        float number(Field f,Object o) {
            try { return f.getFloat(o); }
            catch(IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        boolean flag(Field f,Object o) {
            try { return f.getBoolean(o); }
            catch(IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        void invoke(Method m,Object o,Object... args) {
            try { m.invoke(o,args); }
            catch(InvocationTargetException e) {
                throw new IllegalStateException("Native retained render failed",e.getCause());
            } catch(IllegalAccessException e) { throw new IllegalStateException(e); }
        }
    }
}
