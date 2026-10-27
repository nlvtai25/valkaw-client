package com.valkaw.client.mixin;

import com.valkaw.client.module.utility.FreelookModule;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
	private void valkaw$freelook(Level level, Entity entity, boolean detached, boolean thirdPerson, float partialTick,
			CallbackInfo ci) {
		if (FreelookModule.active()) {
			setRotation(Mth.wrapDegrees(FreelookModule.yaw()), Mth.clamp(FreelookModule.pitch(), -90.0F, 90.0F));
		}
	}
}
