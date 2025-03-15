package com.dji.sdk.voice_control.internal.controller;

import android.content.res.Resources;
import android.graphics.Bitmap;

import java.io.File;

public interface UICallback {
    /**
     * 在主界面上添加文本消息
     *
     * @param owner   消息的发送者，比如 "OWNER_BOT" 或 "OWNER_HUMAN"
     * @param message 消息内容
     */
    void addChatMessage(String owner, String message);

    /**
     * 在主界面上添加图片消息
     *
     * @param owner 消息的发送者
     * @param image 要显示的图片
     */
    void addChatMessage(String owner, Bitmap image);


    File saveBitmapAsFile(Bitmap bitmap, String filename);

    void showSelectObjectDialog();

    String sendQuestionToGPTS(String question, File file, boolean isHistory);

    void sendQuestion(boolean isGPT, String prompt, File imageFile, ControlActivity.OnGptResultListener listener);

    String sendQuestionToGPTSync(String question, File file, boolean isHistory) throws Exception;

    Resources mgetResources();

    File mgetCacheDir();

}

