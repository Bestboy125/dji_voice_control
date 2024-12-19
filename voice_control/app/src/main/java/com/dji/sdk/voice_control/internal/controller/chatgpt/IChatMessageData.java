package com.dji.sdk.voice_control.internal.controller.chatgpt;

import android.graphics.Bitmap;

public interface IChatMessageData {

    int getSize();

    ChatMessage getChatMessage(int position);

    void addChatMessage(String owner, String question);

    void addChatMessage(String owner, String question, Bitmap image);

    String addWelcomeMessage();

    void removeLastChatMessage();

    boolean isBot(String owner);

}
