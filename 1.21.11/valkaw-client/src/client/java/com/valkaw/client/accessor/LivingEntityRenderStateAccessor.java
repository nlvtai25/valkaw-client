package com.valkaw.client.accessor;

/** Lets entity-renderer mixins read/write the per-frame hide-body flag. */
public interface LivingEntityRenderStateAccessor {
	boolean valkaw$hideBody();

	void valkaw$setHideBody(boolean hidden);
}
