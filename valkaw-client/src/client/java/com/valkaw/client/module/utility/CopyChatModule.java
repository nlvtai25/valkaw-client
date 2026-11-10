package com.valkaw.client.module.utility;

import com.valkaw.client.module.Category;
import com.valkaw.client.module.Module;

/** Adds a Copy chip next to every chat line; the chip and its click live in ChatComponentMixin and ChatCopyButtons. */
public class CopyChatModule extends Module {
	public CopyChatModule() {
		super("copy-chat", "Copy Chat", Category.UTILITY);
		setEnabled(true);
	}

	/** Copying is done with the per-line chip, so this module has no key bind. */
	@Override
	public boolean keybindable() {
		return false;
	}
}
