package com.dji.sdk.voice_control.internal.controller.chatgpt;

import android.graphics.Bitmap;

public class ChatMessage {

    private String msg;

    private String owner;

    private Bitmap image;

    public ChatMessage(String owner, String msg) {
        this.owner = owner;
        this.msg = msg;
    }

    public ChatMessage(String owner, String msg,Bitmap image) {
        this.owner = owner;
        this.msg = msg;
        this.image = image;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "msg='" + msg + '\'' +
                ", owner='" + owner + '\'' +
                '}';
    }

    public Bitmap getImage(){
        return image;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
