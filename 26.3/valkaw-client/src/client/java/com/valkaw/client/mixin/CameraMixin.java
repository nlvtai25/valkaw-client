package com.valkaw.client.mixin;

import com.valkaw.client.module.utility.FreelookModule;
import com.valkaw.client.module.utility.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Entity entity;
	@Shadow
	private boolean detached;
	@Shadow
	private float eyeHeight;
	@Shadow
	private float eyeHeightOld;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void move(float x, float y, float z);

	@Shadow
	private float getMaxZoom(float distance) {
		throw new AssertionError();
	}

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void valkaw$zoomFov(float partialTick, CallbackInfoReturnable<Float> cir) {
		float zoom = ZoomModule.fovOverride();
		if (zoom > 0.0F) {
			cir.setReturnValue(zoom);
		}
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void valkaw$freelook(float partialTick, CallbackInfo ci) {
		if (!FreelookModule.active()) {
			this.detached = !net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson();
			return;
		}
		setPosition(Mth.lerp(partialTick, entity.xo, entity.getX()),
				Mth.lerp(partialTick, entity.yo, entity.getY())
						+ Mth.lerp(partialTick, eyeHeightOld, eyeHeight),
				Mth.lerp(partialTick, entity.zo, entity.getZ()));
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
		this.detached = true;
	}
}
