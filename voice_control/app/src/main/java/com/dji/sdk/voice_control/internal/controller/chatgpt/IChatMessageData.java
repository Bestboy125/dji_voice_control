package com.dji.sdk.voice_control.internal.controller.chatgpt;

public interface IChatMessageData {

    int getSize();

    ChatMessage getChatMessage(int position);

    void addChatMessage(String owner, String question);

    String addWelcomeMessage();

    void removeLastChatMessage();

    boolean isBot(String owner);

}
