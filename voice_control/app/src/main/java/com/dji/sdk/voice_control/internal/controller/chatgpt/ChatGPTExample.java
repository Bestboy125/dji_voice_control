package com.dji.sdk.voice_control.internal.controller.chatgpt;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 示例类，演示如何在安卓应用中使用ChatGPTClient
 */
public class ChatGPTExample {
    private static final String TAG = "ChatGPTExample";
    private Context context;

    public ChatGPTExample(Context context) {
        this.context = context;
    }

    /**
     * 处理图像分析请求
     * @param bitmap 要分析的图像
     * @param question 关于图像的问题
     * @param callback 回调接口，返回处理结果
     */
    public void analyzeImage(final Bitmap bitmap, final String question, final ChatGPTCallback callback) {
        // 创建一个异步任务来处理请求，避免阻塞UI线程
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    // 先将Bitmap保存为临时文件
                    File imageFile = saveBitmapToTempFile(bitmap);
                    if (imageFile == null) {
                        return "无法保存图像文件";
                    }

                    // 调用ChatGPTClient的askWithImage方法
                    String response = ChatGPTClient.askWithImage(question, imageFile.getAbsolutePath());
                    
                    // 任务完成后删除临时文件
                    imageFile.delete();
                    
                    return response;
                } catch (NoApiKeyException e) {
                    Log.e(TAG, "API密钥错误: " + e.getMessage());
                    return "API密钥错误: " + e.getMessage();
                } catch (ApiException e) {
                    Log.e(TAG, "API错误: " + e.getMessage());
                    return "API错误: " + e.getMessage();
                } catch (InputRequiredException e) {
                    Log.e(TAG, "输入参数错误: " + e.getMessage());
                    return "输入参数错误: " + e.getMessage();
                } catch (UploadFileException e) {
                    Log.e(TAG, "文件上传错误: " + e.getMessage());
                    return "文件上传错误: " + e.getMessage();
                } catch (Exception e) {
                    Log.e(TAG, "其他错误: " + e.getMessage());
                    return "处理过程中出错: " + e.getMessage();
                }
            }

            @Override
            protected void onPostExecute(String result) {
                // 在UI线程返回结果
                if (callback != null) {
                    callback.onResult(result);
                }
            }
        }.execute();
    }

    /**
     * 处理来自相机的实时帧
     * @param bitmap 相机捕获的帧
     * @param question 关于帧的问题
     * @param callback 回调接口，返回处理结果
     */
    public void analyzeCameraFrame(final Bitmap bitmap, final String question, final ChatGPTCallback callback) {
        // 与analyzeImage相同，但可以根据实时流的需求进行优化
        analyzeImage(bitmap, question, callback);
    }

    /**
     * 将Bitmap保存为临时文件
     * @param bitmap 要保存的图像
     * @return 保存的文件
     */
    private File saveBitmapToTempFile(Bitmap bitmap) {
        File tempDir = new File(context.getCacheDir(), "image_analysis");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        File tempFile = new File(tempDir, "temp_image_" + System.currentTimeMillis() + ".jpg");
        
        try {
            FileOutputStream fos = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            return tempFile;
        } catch (IOException e) {
            Log.e(TAG, "保存图像文件失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 显示简短提示信息
     * @param message 要显示的消息
     */
    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 回调接口，用于接收处理结果
     */
    public interface ChatGPTCallback {
        void onResult(String result);
    }
} 