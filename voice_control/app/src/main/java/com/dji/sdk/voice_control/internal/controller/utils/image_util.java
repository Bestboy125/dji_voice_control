package com.dji.sdk.voice_control.internal.controller.utils;

import android.graphics.Bitmap;

public class image_util {

    /**
     * 裁剪 Bitmap
     *
     * @param source 原始 Bitmap
     * @param x1     左上角 x 坐标
     * @param y1     左上角 y 坐标
     * @param x2     右下角 x 坐标
     * @param y2     右下角 y 坐标
     * @return 裁剪后的 Bitmap
     */
    public Bitmap cropBitmap(Bitmap source, int x1, int y1, int x2, int y2) {
        // 确保裁剪区域在 Bitmap 范围内
        x1 = Math.max(0, x1);
        y1 = Math.max(0, y1);
        x2 = Math.min(x2, source.getWidth());
        y2 = Math.min(y2, source.getHeight());

        // 计算裁剪区域的宽度和高度
        int width = x2 - x1;
        int height = y2 - y1;

        // 防止宽度和高度为负数
        if (width <= 0 || height <= 0) {
            x2 = Math.max(0, x2);
            y2 = Math.max(0, y2);
            x1 = Math.min(x1, source.getWidth());
            y1 = Math.min(y1, source.getHeight());
            width = x1 - x2;
            height = y1 - y2;
            return Bitmap.createBitmap(source, x2, y2, width, height); // 或者返回原图，视需求而定
        }

        return Bitmap.createBitmap(source, x1, y1, width, height);
    }

    /**
     * 裁剪 Bitmap
     *
     * @param source 原始 Bitmap
     * @param x1     左上角 x 坐标
     * @param y1     左上角 y 坐标
     * @param w     右下角 x 坐标
     * @param h     右下角 y 坐标
     * @return 裁剪后的 Bitmap
     */
    public Bitmap cropBitmapwh(Bitmap source, int x1, int y1, int w, int h) {
        return Bitmap.createBitmap(source, x1, y1, w, h);
    }
}
