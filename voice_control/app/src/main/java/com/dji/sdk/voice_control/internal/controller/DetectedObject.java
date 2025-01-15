package com.dji.sdk.voice_control.internal.controller;

import android.graphics.Bitmap;

public class DetectedObject {
    private String id;
    private int x;
    private int y;
    private int width;
    private int height;
    private double conf;
    private String className;
    private Bitmap croppedImage;
    private boolean isSelected;

    // 构造函数
    public DetectedObject(String id, int x, int y, int width, int height, double conf, String className, Bitmap croppedImage) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.conf = conf;
        this.className = className;
        this.croppedImage = croppedImage;
        this.isSelected = false;
    }

    // Getter 和 Setter 方法
    public String getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public double getConf() { return conf; }
    public String getClassName() { return className; }
    public Bitmap getCroppedImage() { return croppedImage; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}

