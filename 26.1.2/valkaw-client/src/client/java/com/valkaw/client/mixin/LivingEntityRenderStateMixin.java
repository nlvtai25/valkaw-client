package com.valkaw.client.mixin;

import com.valkaw.client.accessor.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements LivingEntityRenderStateAccessor {
	@Unique
	private boolean valkaw$hideBody;

	@Override
	public boolean valkaw$hideBody() {
		return this.valkaw$hideBody;
	}

	@Override
	public void valkaw$setHideBody(boolean hidden) {
		this.valkaw$hideBody = hidden;
	}
}
