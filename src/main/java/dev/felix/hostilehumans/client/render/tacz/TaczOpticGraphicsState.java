package dev.felix.hostilehumans.client.render.tacz;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/** Native stencil rendering has no exception cleanup. Keep even a broken resource inside this draw. */
final class TaczOpticGraphicsState implements AutoCloseable {
    // Minecraft 1.20.1 RenderSystem owns twelve shader texture slots. Preserve their driver
    // bindings too: ShaderInstance.clear invalidates its program cache AND unbinds samplers.
    private static final int TEXTURE_SLOTS = 12;
    private final ShaderInstance shader = RenderSystem.getShader();
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final float[] shaderColor = RenderSystem.getShaderColor().clone();
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] shaderTextures = new int[TEXTURE_SLOTS];
    private final int[] textureBindings = new int[TEXTURE_SLOTS];
    private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean[] colors = new boolean[4];
    private final int[] front = stencil(GL11.GL_STENCIL_FUNC, GL11.GL_STENCIL_REF, GL11.GL_STENCIL_VALUE_MASK,
            GL11.GL_STENCIL_WRITEMASK, GL11.GL_STENCIL_FAIL, GL11.GL_STENCIL_PASS_DEPTH_FAIL, GL11.GL_STENCIL_PASS_DEPTH_PASS);
    private final int[] back = stencil(GL20.GL_STENCIL_BACK_FUNC, GL20.GL_STENCIL_BACK_REF, GL20.GL_STENCIL_BACK_VALUE_MASK,
            GL20.GL_STENCIL_BACK_WRITEMASK, GL20.GL_STENCIL_BACK_FAIL, GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL, GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS);
    private final int clear = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);

    TaczOpticGraphicsState() {
        try {
            for (int i = 0; i < TEXTURE_SLOTS; i++) {
                shaderTextures[i] = RenderSystem.getShaderTexture(i);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                textureBindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
        } finally { GlStateManager._activeTexture(activeTexture); }
        try (var memory = MemoryStack.stackPush()) {
            ByteBuffer mask = memory.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colors[i] = mask.get(i) != 0;
        }
    }
    private static int[] stencil(int... names) {
        int[] values = new int[names.length];
        for (int i = 0; i < names.length; i++) values[i] = GL11.glGetInteger(names[i]);
        return values;
    }
    private static void restore(int face, int[] values) {
        GL20.glStencilFuncSeparate(face, values[0], values[1], values[2]);
        GL20.glStencilMaskSeparate(face, values[3]);
        GL20.glStencilOpSeparate(face, values[4], values[5], values[6]);
    }

    private void restoreShader() {
        // ProgramManager/GlStateManager only bind the program; ShaderInstance owns the cache.
        // clear() sets lastProgramId=-1 and lastAppliedShader=null. The next apply therefore
        // MUST bind again, even when the caller's actual program belongs to a shader pack.
        var current = RenderSystem.getShader();
        var invalidator = current != null ? current : shader != null ? shader : GameRenderer.getPositionColorShader();
        try {
            if (invalidator != null) invalidator.clear();
        } finally {
            RenderSystem.setShader(() -> shader);
            ProgramManager.glUseProgram(program);
            // Do not apply() the saved shader: that would upload its now-mutated uniforms, sampler
            // map and blend mode over the caller's program. Native RenderType owns subsequent draws.
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            for (int i = 0; i < TEXTURE_SLOTS; i++) {
                RenderSystem.setShaderTexture(i, shaderTextures[i]);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                GlStateManager._bindTexture(textureBindings[i]);
            }
            GlStateManager._activeTexture(activeTexture);
        }
    }
    @Override public void close() {
        try { restoreShader(); }
        finally {
            RenderSystem.colorMask(colors[0], colors[1], colors[2], colors[3]);
            RenderSystem.depthMask(depthWrite);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            // Update Minecraft's cached front stencil state before restoring front/back driver state.
            RenderSystem.stencilFunc(front[0], front[1], front[2]);
            RenderSystem.stencilMask(front[3]);
            RenderSystem.stencilOp(front[4], front[5], front[6]);
            restore(GL11.GL_FRONT, front);
            restore(GL11.GL_BACK, back);
            RenderSystem.clearStencil(clear);
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }
}
