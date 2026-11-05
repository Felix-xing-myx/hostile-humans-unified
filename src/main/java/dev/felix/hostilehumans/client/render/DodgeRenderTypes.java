package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;

/** The sole 1.20.1 internal seam: read a native composite material's primary texture, never mutate it. */
final class DodgeRenderTypes extends RenderType {
    private record Material(ResourceLocation texture,VertexFormat.Mode mode){}
    private static final Map<RenderType,Optional<RenderType>> SOURCES=new LinkedHashMap<>(256,.75F,true){@Override protected boolean removeEldestEntry(Map.Entry<RenderType,Optional<RenderType>> entry){return size()>256;}};
    private static final Map<Material,RenderType> TYPES=new LinkedHashMap<>(128,.75F,true){@Override protected boolean removeEldestEntry(Map.Entry<Material,RenderType> entry){return size()>128;}};
    private static final Set<RenderType> GLINT=new HashSet<>(List.of(RenderType.armorGlint(),RenderType.armorEntityGlint(),
            RenderType.glintTranslucent(),RenderType.glint(),RenderType.glintDirect(),RenderType.entityGlint(),RenderType.entityGlintDirect()));
    private static final TextureBridge TEXTURES=TextureBridge.open();
    private DodgeRenderTypes(){super("hhu_dodge",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    static RenderType ghost(RenderType source){
        if(source.isOutline()||GLINT.contains(source)||(source.format()!=DefaultVertexFormat.NEW_ENTITY&&source.format()!=DefaultVertexFormat.BLOCK)
                ||(source.mode()!=VertexFormat.Mode.QUADS&&source.mode()!=VertexFormat.Mode.TRIANGLES))return null;
        return SOURCES.computeIfAbsent(source,key->{
            var texture=TEXTURES==null?null:TEXTURES.texture(key);
            if(texture==null)return Optional.empty();
            return Optional.of(TYPES.computeIfAbsent(new Material(texture,key.mode()),material->create("hhu_dodge_gear",
                    DefaultVertexFormat.NEW_ENTITY,material.mode(),256,false,true,CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(material.texture(),false,false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false))));
        }).orElse(null);
    }
    /** Read-only reflection is isolated and fail-soft; no new startup Mixin/AT dependency. */
    private record TextureBridge(Class<?> composite,Method state,Field texture,Method resource){
        static TextureBridge open(){
            try{
                Class<?> type=Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType",false,RenderType.class.getClassLoader());
                return new TextureBridge(type,method(type,"state","m_173265_"),field(CompositeState.class,"textureState","f_110576_"),
                        method(RenderStateShard.EmptyTextureStateShard.class,"cutoutTexture","m_142706_"));
            }catch(ReflectiveOperationException|RuntimeException failure){
                HumanRuntime.LOGGER.warn("Dodge equipment trails unavailable: native 1.20.1 material metadata changed",failure);return null;
            }
        }
        ResourceLocation texture(RenderType type){
            if(!composite.isInstance(type))return null;
            try{Object result=resource.invoke(texture.get(state.invoke(type)));
                return result instanceof Optional<?> optional&&optional.orElse(null) instanceof ResourceLocation id?id:null;
            }catch(ReflectiveOperationException|RuntimeException failure){return null;}
        }
        private static Method method(Class<?> owner,String named,String srg)throws NoSuchMethodException{
            Method value;try{value=owner.getDeclaredMethod(named);}catch(NoSuchMethodException missing){value=owner.getDeclaredMethod(srg);}
            value.setAccessible(true);return value;
        }
        private static Field field(Class<?> owner,String named,String srg)throws NoSuchFieldException{
            Field value;try{value=owner.getDeclaredField(named);}catch(NoSuchFieldException missing){value=owner.getDeclaredField(srg);}
            value.setAccessible(true);return value;
        }
    }
}
