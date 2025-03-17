package com.dji.sdk.voice_control.internal.controller.utils;

import org.json.JSONObject;
import org.json.JSONException;

public class JsonUtils {
    // 用于封装解析结果的简单类
    public static class ParseResult {
        private final String inferenceProcess;
        private final JSONObject jsonData;

        public ParseResult(String inferenceProcess, JSONObject jsonData) {
            this.inferenceProcess = inferenceProcess;
            this.jsonData = jsonData;
        }

        public String getInferenceProcess() {
            return inferenceProcess;
        }

        public JSONObject getJsonData() {
            return jsonData;
        }
    }

    /**
     * 模拟 Python 中的 robust_json_parser 函数
     * @param modelOutput Python中传入的model_output字符串
     * @return 包含 inferenceProcess 和 jsonData 的 ParseResult
     */
    public static ParseResult robustJsonParser(String modelOutput) {
        // 定义标记
        String startMarker = "```json";
        String endMarker = "```";

        // 查找起始与结束位置
        int startIndex = modelOutput.indexOf(startMarker);
        int endIndex = -1;
        if (startIndex != -1) {
            endIndex = modelOutput.indexOf(endMarker, startIndex + startMarker.length());
        }

        // 1. 提取 inferenceProcess（相当于 Python 中的 model_output[:start_index].strip()）
        String inferenceProcess = "";
        if (startIndex != -1) {
            inferenceProcess = modelOutput.substring(0, startIndex).trim();
        }

        // 2. 提取 jsonData（如果找到了 startMarker 和 endMarker）
        JSONObject jsonData = null;
        if (startIndex != -1 && endIndex != -1) {
            // 相当于 Python 中的 model_output[start_index+len(start_marker):end_index].strip()
            String jsonStr = modelOutput.substring(startIndex + startMarker.length(), endIndex).trim();
            try {
                jsonData = new JSONObject(jsonStr);
            } catch (JSONException e) {
                // 如果解析失败可以在这里处理
                e.printStackTrace();
                jsonData = null;
            }
        }

        // 返回结果
        return new ParseResult(inferenceProcess, jsonData);
    }
}
