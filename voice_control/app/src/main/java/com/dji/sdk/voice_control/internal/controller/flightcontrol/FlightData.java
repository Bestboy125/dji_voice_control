package com.dji.sdk.voice_control.internal.controller.flightcontrol;

public class FlightData {

    // 飞行控制数据
    private float mSpeed = 3;
    private float mPitch = 0;
    private float mRoll = 0;
    private float mYaw = 0;
    private float mThrottle = 0;

    // 无人机位置
    private double latitude;
    private double longitude;

    // 无人机数据
    private double mAltitudeData; // 海拔
    private double mDroneHeading;  // 航向
    private double mhs;            // 水平速度 (horizontal speed)
    private double mvs;            // 垂直速度 (vertical speed)
    private double mdistToHome;    // 与家园的距离

    // 构造函数
    public FlightData() {}

    // Getter 和 Setter 方法
    public float getSpeed() {
        return mSpeed;
    }

    public void setSpeed(float mSpeed) {
        this.mSpeed = mSpeed;
    }

    public float getPitch() {
        return mPitch;
    }

    public void setPitch(float mPitch) {
        this.mPitch = mPitch;
    }

    public float getRoll() {
        return mRoll;
    }

    public void setRoll(float mRoll) {
        this.mRoll = mRoll;
    }

    public float getYaw() {
        return mYaw;
    }

    public void setYaw(float mYaw) {
        this.mYaw = mYaw;
    }

    public float getThrottle() {
        return mThrottle;
    }

    public void setThrottle(float mThrottle) {
        this.mThrottle = mThrottle;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getAltitudeData() {
        return mAltitudeData;
    }

    public void setAltitudeData(double mAltitudeData) {
        this.mAltitudeData = mAltitudeData;
    }

    public double getDroneHeading() {
        return mDroneHeading;
    }

    public void setDroneHeading(double mDroneHeading) {
        this.mDroneHeading = mDroneHeading;
    }

    public double getMhs() {
        return mhs;
    }

    public void setMhs(double mhs) {
        this.mhs = mhs;
    }

    public double getMvs() {
        return mvs;
    }

    public void setMvs(double mvs) {
        this.mvs = mvs;
    }

    public double getMdistToHome() {
        return mdistToHome;
    }

    public void setMdistToHome(double mdistToHome) {
        this.mdistToHome = mdistToHome;
    }

    // 输出飞行数据的字符串
    @Override
    public String toString() {
        return "FlightData{" +
                "mSpeed=" + mSpeed +
                ", mPitch=" + mPitch +
                ", mRoll=" + mRoll +
                ", mYaw=" + mYaw +
                ", mThrottle=" + mThrottle +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", mAltitudeData=" + mAltitudeData +
                ", mDroneHeading=" + mDroneHeading +
                ", mhs=" + mhs +
                ", mvs=" + mvs +
                ", mdistToHome=" + mdistToHome +
                '}';
    }
}

