package com.valkaw.client.mixin;

import com.valkaw.client.module.utility.FreelookModule;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Entity entity;
	@Shadow
	private float eyeHeight;
	@Shadow
	private float eyeHeightOld;
	@Shadow
	private float partialTickTime;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void move(float x, float y, float z);

	@Shadow
	protected abstract float getMaxZoom(float distance);

	@Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
	private void valkaw$freelook(Level level, Entity entity, boolean detached, boolean thirdPerson, float partialTick,
			CallbackInfo ci) {
		if (!FreelookModule.active()) {
			return;
		}
		setPosition(Mth.lerp(partialTickTime, entity.xo, entity.getX()),
				Mth.lerp(partialTickTime, entity.yo, entity.getY())
						+ Mth.lerp(partialTickTime, eyeHeightOld, eyeHeight),
				Mth.lerp(partialTickTime, entity.zo, entity.getZ()));
		float yaw = Mth.wrapDegrees(FreelookModule.yaw());
		float pitch = Mth.clamp(FreelookModule.pitch(), -90.0F, 90.0F);
		if (FreelookModule.front()) {
			yaw = Mth.wrapDegrees(yaw + 180.0F);
			pitch = -pitch;
		}
		setRotation(yaw, pitch);
		float scale = entity instanceof LivingEntity living ? living.getScale() : 1.0F;
		float distance = entity instanceof LivingEntity living
				? (float) living.getAttributeValue(Attributes.CAMERA_DISTANCE) : 4.0F;
		move(-getMaxZoom(scale * distance), 0.0F, 0.0F);
	}
}
