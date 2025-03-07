package com.dji.sdk.voice_control.internal.controller.gimbal;

import com.dji.sdk.voice_control.internal.utils.CallbackHandlers;

import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.sdk.base.BaseProduct;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class gimbalControl {

    //region 云台相关
    private Gimbal gimbal = null;
    private int currentGimbalId = 0;

    //region 云台控制

    /**
     * 让云台俯视（垂直向下）
     */
    public void rotateGimbalDownwardView() {
        // 对于绝大多数 DJI 云台来说，-90° 表示正对地面
        rotateGimbal(-90.0f, 0.0f, 0.0f);
    }

    /**
     * 让云台前视（水平向前）
     */
    public void rotateGimbalForwardView() {
        // 0° 表示水平前视
        rotateGimbal(0.0f, 0.0f, 0.0f);
    }

    /**
     * 以绝对坐标系旋转相机云台
     * @param pitchValue
     * @param yawValue
     * @param rollValue
     */
    private void rotateGimbal(float pitchValue,float yawValue,float rollValue) {

        Rotation rotation = new Rotation.Builder().pitch(pitchValue)
                .mode(RotationMode.ABSOLUTE_ANGLE)
                .yaw(yawValue)
                .roll(rollValue)
                .time(0)
                .build();

        sendRotateGimbalCommand(rotation);
    }

    /**
     * 发送旋转命令到云台
     * @param rotation
     */
    private void sendRotateGimbalCommand(Rotation rotation) {

        Gimbal gimbal = getGimbalInstance();
        if (gimbal == null) {
            return;
        }
        gimbal.rotate(rotation, new CallbackHandlers.CallbackToastHandler());
    }

    /**
     * 得到云台对象实例
     * @return
     */
    private Gimbal getGimbalInstance() {
        if (gimbal == null) {
            initGimbal();
        }
        return gimbal;
    }

    /**
     * 初始化云台
     */
    private void initGimbal() {
        if (DJISDKManager.getInstance() != null) {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product != null) {
                if (product instanceof Aircraft) {
                    gimbal = ((Aircraft) product).getGimbals().get(currentGimbalId);
                } else {
                    gimbal = product.getGimbal();
                }
            }
        }
    }
    //endregion
}
