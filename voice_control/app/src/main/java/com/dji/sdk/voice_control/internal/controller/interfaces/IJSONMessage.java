package com.dji.sdk.voice_control.internal.controller.interfaces;

import org.json.JSONArray;

public interface IJSONMessage {

    void addUserMessage(String question);

    void addBotMessage(String question);

    void removeNotNeededMessage();

    JSONArray getArray();
}
