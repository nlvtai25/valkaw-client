package com.valkaw.client;

/** Implemented by the ChatScreen mixin so hosts can seed the chat input. */
public interface SnapChatAccess {
	void valkawSetInput(String text);
}
