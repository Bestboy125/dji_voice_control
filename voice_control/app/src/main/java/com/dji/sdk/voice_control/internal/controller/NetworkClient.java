package com.dji.sdk.voice_control.internal.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class NetworkClient {

    private static String SERVER_URL = "http://122.207.106.69:25126";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private OkHttpClient client;
    private Gson gson;

    public NetworkClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)      // 连接超时：30秒
                .readTimeout(300, TimeUnit.SECONDS)        // 读取超时：5分钟，适应模型处理时间
                .writeTimeout(60, TimeUnit.SECONDS)        // 写入超时：60秒
                .build();
        gson = new Gson();
    }
    /**
     * 修改后端连接地址
     */
    public void changeUrl(String url){
        SERVER_URL = "http://" + url;
    }

    /**
     * 获取服务基本信息
     * GET /
     * @return 服务器响应的 JsonObject
     * @throws IOException
     */
    public JsonObject getServiceInfo() throws IOException {
        String url = SERVER_URL + "/";
        Request request = new Request.Builder()
                .url(url)
                .get()
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
     * 健康检查接口
     * GET /health
     * @return 服务器响应的 JsonObject
     * @throws IOException
     */
    public JsonObject getHealthStatus() throws IOException {
        String url = SERVER_URL + "/health";
        Request request = new Request.Builder()
                .url(url)
                .get()
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
     * 目标检测接口
     * POST /detect_target
     * @param imageFile 需要检测的图像文件
     * @param classType 目标类别，可选，默认为 "object"
     * @return 服务器响应的 JsonObject
     * @throws IOException
     */
    public JsonObject detectTarget(File imageFile, String classType) throws IOException {
        String url = SERVER_URL + "/detect_target";
        
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", imageFile.getName(),
                        RequestBody.create(MediaType.parse("image/*"), imageFile));
        
        if (classType != null && !classType.isEmpty()) {
            builder.addFormDataPart("class_type", classType);
        }
        
        RequestBody requestBody = builder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
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
     * 深度估计接口
     * POST /depth_estimate
     * @param image1 第一视角图像
     * @param image2 第二视角图像
     * @param image3 第三视角图像
     * @param cameraParams1 第一相机参数的 JSON 字符串
     * @param cameraParams2 第二相机参数的 JSON 字符串
     * @param cameraParams3 第三相机参数的 JSON 字符串
     * @param classType 目标类别，可选
     * @param detectImgIdx 用于检测的图像索引 (0-2)，可选
     * @param coarseDepthMin 粗采样最小深度，可选
     * @param coarseDepthMax 粗采样最大深度，可选
     * @param coarseSamples 粗采样点数，可选
     * @param fineSamples 精采样点数，可选
     * @param tolerance 重投影误差容差，可选
     * @return 服务器响应的 JsonObject
     * @throws IOException
     */
    public JsonObject estimateDepth(File image1, File image2, File image3,
                                   String cameraParams1, String cameraParams2, String cameraParams3,
                                   String classType, Integer detectImgIdx,
                                   Float coarseDepthMin, Float coarseDepthMax,
                                   Integer coarseSamples, Integer fineSamples,
                                   Float tolerance) throws IOException {
        String url = SERVER_URL + "/depth_estimate";
        
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image1", image1.getName(),
                        RequestBody.create(MediaType.parse("image/*"), image1))
                .addFormDataPart("image2", image2.getName(),
                        RequestBody.create(MediaType.parse("image/*"), image2))
                .addFormDataPart("image3", image3.getName(),
                        RequestBody.create(MediaType.parse("image/*"), image3))
                .addFormDataPart("camera_params1", cameraParams1)
                .addFormDataPart("camera_params2", cameraParams2)
                .addFormDataPart("camera_params3", cameraParams3);
        
        // 添加可选参数
        if (classType != null && !classType.isEmpty()) {
            builder.addFormDataPart("class_type", classType);
        }
        if (detectImgIdx != null) {
            builder.addFormDataPart("detect_img_idx", String.valueOf(detectImgIdx));
        }
        if (coarseDepthMin != null) {
            builder.addFormDataPart("coarse_depth_min", String.valueOf(coarseDepthMin));
        }
        if (coarseDepthMax != null) {
            builder.addFormDataPart("coarse_depth_max", String.valueOf(coarseDepthMax));
        }
        if (coarseSamples != null) {
            builder.addFormDataPart("coarse_samples", String.valueOf(coarseSamples));
        }
        if (fineSamples != null) {
            builder.addFormDataPart("fine_samples", String.valueOf(fineSamples));
        }
        if (tolerance != null) {
            builder.addFormDataPart("tolerance", String.valueOf(tolerance));
        }
        
        RequestBody requestBody = builder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
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
     * 深度估计接口的简化版本，使用默认参数
     * @param image1 第一视角图像
     * @param image2 第二视角图像  
     * @param image3 第三视角图像
     * @param cameraParams1 第一相机参数的 JSON 字符串
     * @param cameraParams2 第二相机参数的 JSON 字符串
     * @param cameraParams3 第三相机参数的 JSON 字符串
     * @return 服务器响应的 JsonObject
     * @throws IOException
     */
    public JsonObject estimateDepth(File image1, File image2, File image3,
                                   String cameraParams1, String cameraParams2, String cameraParams3) throws IOException {
        return estimateDepth(image1, image2, image3, cameraParams1, cameraParams2, cameraParams3,
                           null, null, null, null, null, null, null);
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
