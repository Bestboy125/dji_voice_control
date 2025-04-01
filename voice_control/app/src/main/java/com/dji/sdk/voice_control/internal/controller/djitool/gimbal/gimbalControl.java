package com.dji.sdk.voice_control.internal.controller.djitool.gimbal;

import android.util.Log;

import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.djidemo.utils.CallbackHandlers;

import java.util.Map;

import dji.common.error.DJIError;
import dji.common.gimbal.CapabilityKey;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.util.CommonCallbacks;
import dji.common.util.DJIParamCapability;
import dji.common.util.DJIParamMinMaxCapability;
import dji.sdk.base.BaseProduct;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class gimbalControl {

    // Add a debug tag for logging
    private static final String TAG = "GimbalControl";

    //region 云台相关
    private Gimbal gimbal = null;
    private int currentGimbalId = 0;

    //region 云台控制

    /**
     * 让云台俯视（垂直向下）
     */
    public void rotateGimbalDownwardView() {
        Log.d(TAG, "rotateGimbalDownwardView: Rotating gimbal to downward position (-90°)");
        // 对于绝大多数 DJI 云台来说，-90° 表示正对地面
        rotateGimbalAbsolute(-90f, 0f, 0f);
    }

    /**
     * 让云台前视（水平向前）
     */
    public void rotateGimbalForwardView() {
        Log.d(TAG, "rotateGimbalForwardView: Rotating gimbal to forward position (0°)");
        // 0° 表示水平前视
        rotateGimbalAbsolute(0f, 0f, 0f);
    }

    /**
     * 以绝对坐标系旋转相机云台
     * @param pitchValue
     * @param yawValue
     * @param rollValue
     */
    public void rotateGimbalAbsolute(float pitchValue, float yawValue, float rollValue) {
        Log.d(TAG, "rotateGimbalAbsolute: pitch=" + pitchValue + ", yaw=" + yawValue + ", roll=" + rollValue);

        Rotation.Builder builder = new Rotation.Builder().pitch(pitchValue)
                .mode(RotationMode.ABSOLUTE_ANGLE)
                .yaw(yawValue)
                .time(0.5);

        sendRotateGimbalCommand(builder.build());
    }

    /**
     * 以绝对坐标系旋转相机云台
     * @param pitchValue
     */
    public void pitchGimbalAbsolute(float pitchValue) {
        Log.d(TAG, "rotateGimbalAbsolute: pitch=" + pitchValue);

        Rotation.Builder builder = new Rotation.Builder().pitch(pitchValue)
                .mode(RotationMode.ABSOLUTE_ANGLE)
                .time(0.5);

        sendRotateGimbalCommand(builder.build());
    }


    /**
     * 以绝对坐标系旋转相机云台
     * @param pitchValue
     * @param yawValue
     * @param rollValue
     */
    private void rotateGimbalRelative(float pitchValue, float yawValue, float rollValue) {
        Log.d(TAG, "rotateGimbalRelative: pitch=" + pitchValue + ", yaw=" + yawValue + ", roll=" + rollValue);

        Rotation rotation = new Rotation.Builder().pitch(pitchValue)
                .mode(RotationMode.RELATIVE_ANGLE)
                .yaw(yawValue)
                .time(0)
                .build();

        sendRotateGimbalCommand(rotation);
    }

    /**
     * 发送旋转命令到云台
     * @param rotation
     */
    private void sendRotateGimbalCommand(Rotation rotation) {
        Log.d(TAG, "sendRotateGimbalCommand: mode=" + rotation.getMode() + 
              ", pitch=" + rotation.getPitch() + 
              ", yaw=" + rotation.getYaw() + 
              ", roll=" + rotation.getRoll());

        Gimbal gimbal = getGimbalInstance();
        if (gimbal == null) {
            Log.e(TAG, "sendRotateGimbalCommand: Failed - gimbal instance is null");
            return;
        }
        
        gimbal.rotate(rotation, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "Gimbal rotation command sent successfully");
                } else {
                    Log.e(TAG, "Gimbal rotation error: " + djiError.getDescription());
                }
            }
        });
    }

    /**
     * 得到云台对象实例
     * @return
     */
    private Gimbal getGimbalInstance() {
        if (gimbal == null) {
            Log.d(TAG, "getGimbalInstance: Gimbal is null, initializing...");
            initGimbal();
        }
        
        if (gimbal == null) {
            Log.e(TAG, "getGimbalInstance: Failed to initialize gimbal");
        } else {
            Number minpitch = ((DJIParamMinMaxCapability) (gimbal.getCapabilities().get(CapabilityKey.ADJUST_PITCH))).getMin();
            Number maxpitch = ((DJIParamMinMaxCapability) (gimbal.getCapabilities().get(CapabilityKey.ADJUST_PITCH))).getMax();
            Number minyaw = ((DJIParamMinMaxCapability) (gimbal.getCapabilities().get(CapabilityKey.ADJUST_YAW))).getMin();
            Number maxyaw = ((DJIParamMinMaxCapability) (gimbal.getCapabilities().get(CapabilityKey.ADJUST_YAW))).getMax();
            Number minroll = ((DJIParamMinMaxCapability) (gimbal.getCapabilities().get(CapabilityKey.ADJUST_ROLL))).getMin();
            Number maxroll = ((DJIParamMinMaxCapability) (gimbal.getCapabilities().get(CapabilityKey.ADJUST_ROLL))).getMax();
            Log.d(TAG, "minpitch: " + minpitch + ", maxpitch: " + maxpitch + ", minyaw: " + minyaw + ", maxyaw: " + maxyaw + ", minroll: " + minroll + ", maxroll: " + maxroll);
            Log.d(TAG, "getGimbalInstance: Gimbal instance obtained successfully");
        }
        
        return gimbal;
    }

    /**
     * 初始化云台
     */
    private void initGimbal() {
        Log.d(TAG, "initGimbal: Attempting to initialize gimbal");
        gimbal = DJISampleApplication.getGimbal();
        
        if (gimbal != null) {
            Log.d(TAG, "initGimbal: Gimbal initialized successfully");
        } else {
            Log.e(TAG, "initGimbal: Failed to initialize gimbal - returned null from DJISampleApplication");
        }
    }
    //endregion
}
