package com.dji.sdk.voice_control.internal.controller.chatgpt;

import android.util.Base64;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * GPTS 类 - 使用 OkHttp 异步请求
 */
public class GPTS {

    private String apiKey;
    private String modelName;
    private float temperature;
    private float topP;
    private int maxTokens;

    private final List<String> supportedModelNames = Arrays.asList(
            "gpt-3.5-turbo",
            "gpt-4-turbo-preview",
            "gpt-4-turbo",
            "gpt-4-vision-preview",
            "gpt-4-all",
            "GPTS",
            "gpt-4-1106-preview",
            "gpt-4-0125-preview",
            "gpt-4-turbo-2024-04-09",
            "gpt-4o"
    );

    private String defaultPrompt = "You are a useful assistant who can efficiently complete user-specified tasks or answer user questions well.";
    private int defaultSleepTime = 2; // 秒（此示例暂未使用，如果需要重试，可用这个）
    private int maxRetries = 5;       // 最大重试次数（此示例暂未使用，如果需要重试，可在异步逻辑中实现）

    // API 地址（已替换为最新地址）
    private String url = "https://chatapi.onechats.top/v1/chat/completions";

    // 构造函数
    public GPTS(@Nullable String apiKey,
                String modelName,
                float temperature,
                float topP,
                int maxTokens) {
        this.apiKey = (apiKey != null) ? apiKey : System.getenv("OPENAI_API_KEY");
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key is missing!");
        }
        if (!supportedModelNames.contains(modelName)) {
            throw new IllegalArgumentException("Model name should be one of " + supportedModelNames.toString());
        }
        this.modelName = modelName;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
    }

    /**
     * 动态设置字段值
     */
    public void setAttr(String name, Object value) throws NoSuchFieldException, IllegalAccessException {
        switch (name) {
            case "apiKey":
                this.apiKey = (String) value;
                break;
            case "modelName":
                this.modelName = (String) value;
                break;
            case "temperature":
                this.temperature = (float) value;
                break;
            case "topP":
                this.topP = (float) value;
                break;
            case "maxTokens":
                this.maxTokens = (int) value;
                break;
            case "defaultPrompt":
                this.defaultPrompt = (String) value;
                break;
            case "defaultSleepTime":
                this.defaultSleepTime = (int) value;
                break;
            case "maxRetries":
                this.maxRetries = (int) value;
                break;
            default:
                throw new NoSuchFieldException("Field '" + name + "' does not exist.");
        }
    }

    /**
     * 生成默认 Payload
     */
    private JSONObject getPayload(@Nullable String prompt) throws JSONException {
        if (prompt == null || prompt.isEmpty()) {
            prompt = defaultPrompt;
        }
        JSONObject payload = new JSONObject();
        payload.put("model", modelName);
        payload.put("temperature", temperature);
        payload.put("top_p", topP);
        payload.put("max_tokens", maxTokens);

        // 构造 messages 数组
        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");

        // system 内容 (type = text)
        JSONArray sysContent = new JSONArray();
        JSONObject sysText = new JSONObject();
        sysText.put("type", "text");
        sysText.put("text", prompt);
        sysContent.put(sysText);

        sysMsg.put("content", sysContent);
        messages.put(sysMsg);

        payload.put("messages", messages);
        return payload;
    }

    /**
     * 异步方法：发起聊天请求
     *
     * @param question   用户提问
     * @param imageFiles 图片路径（String 或 String[]）
     * @param prompt     可选的 system prompt
     * @param history    历史上下文（上一轮的 payload）
     * @param callback   结果回调
     */
    public void chatAsync(String question,
                          @Nullable Object imageFiles,
                          @Nullable String prompt,
                          @Nullable JSONObject history,
                          GPTSCallback callback) {

        // 拼装 payload
        JSONObject payload;
        try {
            payload = (history != null) ? history : getPayload(prompt);
            JSONArray messages = payload.getJSONArray("messages");

            // 追加用户消息
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            JSONArray userContent = new JSONArray();

            // 用户文本
            JSONObject userText = new JSONObject();
            userText.put("type", "text");
            userText.put("text", question);
            userContent.put(userText);

            // 如果有图像文件
            if (imageFiles != null) {
                // 只有包含 gpt-4 的模型才支持图像
                if (!modelName.contains("gpt-4")) {
                    throw new IllegalArgumentException("Image input is only supported for GPT-4 models.");
                }
                String[] imagesArr;
                if (imageFiles instanceof String) {
                    imagesArr = new String[]{(String) imageFiles};
                } else if (imageFiles instanceof String[]) {
                    imagesArr = (String[]) imageFiles;
                } else {
                    throw new IllegalArgumentException("imageFiles must be String or String[]");
                }

                // 依次处理每个图像
                for (String imgPath : imagesArr) {
                    JSONObject imageObj = new JSONObject();
                    imageObj.put("type", "image_url");
                    String mimeType = guessMimeType(imgPath);
                    String base64Img = encodeImage(imgPath);
                    JSONObject urlObj = new JSONObject();
                    urlObj.put("url", "data:" + mimeType + ";base64," + base64Img);
                    imageObj.put("image_url", urlObj);
                    userContent.put(imageObj);
                }
            }

            userMsg.put("content", userContent);
            messages.put(userMsg);

            // 准备发起请求
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, payload.toString());

            Request request = new Request.Builder()
                    .url(url)
                    .headers(Headers.of(getHeaders()))
                    .post(body)
                    .build();

            // 异步请求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // 请求失败
                    callback.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    // HTTP状态码非200也算失败
                    if (!response.isSuccessful()) {
                        callback.onError(new IOException("Unexpected response code: " + response.code()));
                        response.close();
                        return;
                    }
                    try {
                        String respString = (response.body() != null) ? response.body().string() : "";
                        JSONObject responseJson = new JSONObject(respString);

                        // 解析结果
                        String output = parseResponse(responseJson);

                        // 将 assistant 的回复也放入 payload，以便多轮对话
                        JSONObject assistantMsg = new JSONObject();
                        assistantMsg.put("role", "assistant");
                        JSONArray assistantContent = new JSONArray();
                        JSONObject assistantText = new JSONObject();
                        assistantText.put("type", "text");
                        assistantText.put("text", output);
                        assistantContent.put(assistantText);
                        assistantMsg.put("content", assistantContent);
                        messages.put(assistantMsg);

                        // 回调成功
                        GPTSResult gptsResult = new GPTSResult(output, payload);
                        callback.onSuccess(gptsResult);

                    } catch (Exception e) {
                        callback.onError(e);
                    } finally {
                        response.close();
                    }
                }
            });

        } catch (Exception e) {
            // 拼装 payload 失败或其他异常
            callback.onError(e);
        }
    }

    /**
     * 解析返回的 JSON，提取最后一条 assistant 文本
     */
    private String parseResponse(JSONObject responseJson) throws JSONException {
        JSONArray choices = responseJson.getJSONArray("choices");
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject messageObj = firstChoice.getJSONObject("message");
        return messageObj.getString("content");
    }

    /**
     * 获取请求头
     */
    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }

    /**
     * 将图像文件读取为 base64
     */
    private String encodeImage(String imagePath) throws IOException {
        File file = new File(imagePath);
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        int readBytes = fis.read(data);
        fis.close();
        if (readBytes != file.length()) {
            throw new IOException("Could not read the full file: " + imagePath);
        }
        // 使用 android.util.Base64
        return Base64.encodeToString(data, Base64.DEFAULT);
    }

    /**
     * 简易推断文件类型，可改用更可靠的库
     */
    private String guessMimeType(String filePath) {
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        } else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }

    /**
     * GPTS 结果类：包含输出文本和更新后的历史上下文
     */
    public static class GPTSResult {
        public String output;
        public JSONObject history;

        public GPTSResult(String output, JSONObject history) {
            this.output = output;
            this.history = history;
        }
    }
}
