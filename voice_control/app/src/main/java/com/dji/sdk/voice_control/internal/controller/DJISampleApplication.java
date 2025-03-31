package com.dji.sdk.voice_control.internal.controller;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import androidx.multidex.MultiDex;

import java.util.Objects;

import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.mission.hotpoint.HotpointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointV2MissionOperator;
import dji.sdk.products.Aircraft;
import dji.sdk.products.HandHeld;
import dji.sdk.sdkmanager.BluetoothProductConnector;
import dji.sdk.sdkmanager.DJISDKManager;

/**
 * Main application
 */
public class DJISampleApplication extends Application {

    public static final String TAG = DJISampleApplication.class.getName();
    public static final String FLAG_CONNECTION_CHANGE = "com_dji_simulatorDemo_connection_change";
    private static BaseProduct product;
    private static BluetoothProductConnector bluetoothConnector = null;
    private static Bus bus = new Bus(ThreadEnforcer.ANY);
    private static Application app = null;
    private static Context mContext;

    /**
     * Gets instance of the specific product connected after the
     * API KEY is successfully validated. Please make sure the
     * API_KEY has been added in the Manifest
     */
    public static synchronized BaseProduct getProductInstance() {
        product = DJISDKManager.getInstance().getProduct();
        return product;
    }

    public static synchronized BluetoothProductConnector getBluetoothProductConnector() {
        bluetoothConnector = DJISDKManager.getInstance().getBluetoothProductConnector();
        return bluetoothConnector;
    }

    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof Aircraft;
    }

    public static boolean isHandHeldConnected() {
        return getProductInstance() != null && getProductInstance() instanceof HandHeld;
    }

    public static synchronized Aircraft getAircraftInstance() {
        if (!isAircraftConnected()) {
            return null;
        }
        return (Aircraft) getProductInstance();
    }

    public static synchronized Camera getCameraInstance() {
        if (getProductInstance() == null) return null;
        Camera camera = null;
        if (getProductInstance() instanceof Aircraft){
            camera = ((Aircraft) getProductInstance()).getCamera();
        } else if (getProductInstance() instanceof HandHeld) {
            camera = ((HandHeld) getProductInstance()).getCamera();
        }
        return camera;
    }

    public static synchronized WaypointMissionOperator getWaypointMissionOperator() {
        if (getProductInstance() == null) {
            Log.e(TAG, "getWaypointMissionOperator: Product instance is null - drone not connected?");
            return null;
        }

        WaypointMissionOperator operator = null;
        try {
            operator = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            if (operator == null) {
                Log.e(TAG, "getWaypointMissionOperator: Failed to get waypointMissionV2Operator from MissionControl");
            } else {
                Log.d(TAG, "getWaypointMissionOperator: Successfully obtained waypointMissionV2Operator");
            }
        } catch (Exception e) {
            Log.e(TAG, "getWaypointMissionOperator: Exception getting waypointMissionV2Operator: " + e.getMessage());
        }
        
        return operator;
    }

    public static synchronized HotpointMissionOperator getHotMissionOperator() {
        if (getProductInstance() == null) {
            Log.e(TAG, "getHotMissionOperator: Product instance is null - drone not connected?");
            return null;
        }

        HotpointMissionOperator operator = null;
        try {
            operator = DJISDKManager.getInstance().getMissionControl().getHotpointMissionOperator();
            if (operator == null) {
                Log.e(TAG, "getHotMissionOperator: Failed to get waypointMissionV2Operator from MissionControl");
            } else {
                Log.d(TAG, "getHotMissionOperator: Successfully obtained waypointMissionV2Operator");
            }
        } catch (Exception e) {
            Log.e(TAG, "getHotMissionOperator: Exception getting waypointMissionV2Operator: " + e.getMessage());
        }

        return operator;
    }

    public static synchronized FlightController getFlightController(){
        return Objects.requireNonNull(getAircraftInstance()).getFlightController();
    }

    public static synchronized Gimbal getGimbal(){
        return Objects.requireNonNull(getAircraftInstance()).getGimbal();
    }

    public static synchronized HandHeld getHandHeldInstance() {
        if (!isHandHeldConnected()) {
            return null;
        }
        return (HandHeld) getProductInstance();
    }



    public static Application getInstance() {
        return DJISampleApplication.app;
    }

    public static Bus getEventBus() {
        return bus;
    }

    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        MultiDex.install(this);
        com.cySdkyc.clx.Helper.install(this);
        mContext = getApplicationContext();
        app = this;
    }

    public static Context getContext() {
        return mContext;
    }

}