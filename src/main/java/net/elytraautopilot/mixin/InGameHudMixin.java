package net.elytraautopilot.mixin;

import net.elytraautopilot.ElytraAutoPilot;
import net.elytraautopilot.config.ModConfig;
import net.elytraautopilot.utils.Hud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
	@Inject(at = @At(value = "RETURN"), method = "render")
	public void renderPost(MatrixStack matrixStack, float f, CallbackInfo ci) {
		// TODO allow drawing on background of current screen
		if (!ci.isCancelled()) {
			MinecraftClient minecraftClient = MinecraftClient.getInstance();
			if (minecraftClient.currentScreen == null && ElytraAutoPilot.calculateHud) {
				if (Hud.hudString != null) {
					float stringX = ModConfig.gui.guiX;
					float stringY = ModConfig.gui.guiY;
					float scale = (float) ModConfig.gui.guiScale/100;
					matrixStack.scale(scale, scale, scale);
					for (int i = 0; i < Hud.hudString.length; i++) {
						minecraftClient.textRenderer.drawWithShadow(matrixStack, Hud.hudString[i], stringX, stringY, 0xFFFFFF);
						stringY += minecraftClient.textRenderer.fontHeight + 1;
					}
				}
			}
		}
	}
}
