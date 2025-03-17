package com.dji.sdk.voice_control.internal.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class NetworkClient {

    private static String SERVER_URL = "http://122.207.106.69:25440";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private OkHttpClient client;
    private Gson gson;

    public NetworkClient() {
        client = new OkHttpClient();
        gson = new Gson();
    }
    /**
     * 修改后端连接地址
     */
    public void changeUrl(String url){
        SERVER_URL = "http://" + url;
    }

    /**
     * 发送 /detect 请求
     *
     * @param base64Image Base64 编码的图像字符串
     * @return 服务器响应的 JsonObject
     * @throws IOException
     */
    public JsonObject sendDetectRequest(String base64Image) throws IOException {
        String url = SERVER_URL + "/detect";
        JsonObject payload = new JsonObject();
        payload.addProperty("base64_img", base64Image);
        String jsonPayload = gson.toJson(payload);

        RequestBody body = RequestBody.create(JSON, jsonPayload);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }

    /**
     * 发送 /init_tracker 请求
     *
     * @param detId 选择的目标 ID
     * @return 服务器响应的 JsonObject
     * @throws IOException
     */
    public JsonObject sendInitTrackerRequest(int detId) throws IOException {
        String url = SERVER_URL + "/init_tracker";
        JsonObject payload = new JsonObject();
        payload.addProperty("det_id", detId);
        String jsonPayload = gson.toJson(payload);

        RequestBody body = RequestBody.create(JSON, jsonPayload);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }

    /**
     * 发送 /track_frame 请求
     *
     * @param base64Image Base64 编码的图像字符串
     * @return 服务器响应的 JsonObject
     * @throws IOException
     */
    public JsonObject sendTrackFrameRequest(String base64Image) throws IOException {
        String url = SERVER_URL + "/track_frame";
        JsonObject payload = new JsonObject();
        payload.addProperty("base64_img", base64Image);
        String jsonPayload = gson.toJson(payload);

        RequestBody body = RequestBody.create(JSON, jsonPayload);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }
}
