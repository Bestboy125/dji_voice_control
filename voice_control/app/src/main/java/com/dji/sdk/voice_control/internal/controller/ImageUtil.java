package com.dji.sdk.voice_control.internal.controller;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public class ImageUtil {

    /**
     * 将图像文件转换为 Base64 字符串。
     *
     * @param imagePath 图像文件的路径
     * @return Base64 编码的字符串
     */
    public static String imageToBase64(String imagePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) return null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 压缩图像以减少 Base64 字符串的大小
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] byteArray = baos.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    /**
     * 将 Base64 字符串解码为 Bitmap 图像。
     *
     * @param base64String Base64 编码的图像字符串
     * @return 解码后的 Bitmap 图像
     */
    public static Bitmap decodeBase64Image(String base64String) {
        byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }
}
