package com.dji.sdk.voice_control.internal.controller.chatgpt;

public interface GPTSCallback {
    /**
     * 当网络请求成功并且解析成功后，返回 GPTSResult
     */
    void onSuccess(GPTS.GPTSResult result);

    /**
     * 当网络或解析发生异常时，会回调 onError
     */
    void onError(Exception e);
}
