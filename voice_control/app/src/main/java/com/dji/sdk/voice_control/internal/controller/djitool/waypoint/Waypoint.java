package com.dji.sdk.voice_control.internal.controller.djitool.waypoint;

import static com.dji.sdk.voice_control.internal.djidemo.utils.ToastUtils.setResultToToast;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.error.DJIError;
import dji.common.error.DJIWaypointV2Error;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypointv2.Action.WaypointV2Action;
import dji.common.mission.waypointv2.WaypointV2;
import dji.common.mission.waypointv2.WaypointV2Mission;
import dji.common.mission.waypointv2.WaypointV2MissionTypes;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.RTK;
import dji.sdk.mission.waypoint.WaypointV2MissionOperator;

import com.amap.api.maps2d.model.Marker;

public class Waypoint {

    private static final String TAG = "Waypoint";
    private View view;
    private Context mContext;

    Waypoint(View v){
        this.view = v;
        Log.d(TAG, "Waypoint initialized with view");
    }

    public Waypoint(Context mContext){
        this.mContext = mContext;
        Log.d(TAG, "Waypoint initialized with context");
    }

    public Waypoint(){
        Log.d(TAG, "Waypoint initialized with default constructor");
    }

    //region 数据结构

    public float altitude = 100.0f;
    public float mSpeed = 10.0f;

    public static WaypointV2Mission.Builder waypointMissionBuilder;
    public WaypointV2MissionOperator instance;
    public WaypointV2MissionTypes.MissionFinishedAction mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.NO_ACTION;
    public WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    public WaypointV2MissionTypes.MissionGotoWaypointMode firstMode = WaypointV2MissionTypes.MissionGotoWaypointMode.SAFELY;
    public boolean canUploadMission;
    public boolean canStartMission;
    //endregion

    public int getWaypointCount(WaypointV2MissionOperator instance){
        int count = waypointMissionBuilder.getWaypointCount();
        Log.d(TAG, "getWaypointCount: " + count);
        return count;
    }


    public void AddWaypoint(double latitude,double longitude) {
        Log.d(TAG, "Adding waypoint at latitude: " + latitude + ", longitude: " + longitude + ", altitude: " + altitude);
        WaypointV2 mWaypoint = new WaypointV2.Builder()
                .setAltitude(altitude)
                .setCoordinate(new LocationCoordinate2D(latitude, longitude))
                .build();
        //Add Waypoints to Waypoint arraylist;
        if (waypointMissionBuilder == null) {
            Log.d(TAG, "waypointMissionBuilder was null, creating new instance");
            waypointMissionBuilder = new WaypointV2Mission.Builder();
        }
        waypointMissionBuilder.addWaypoint(mWaypoint);
        Log.d(TAG, "Waypoint added successfully, total waypoints: " + waypointMissionBuilder.getWaypointCount());
    }

    public void RemoveWaypoint(int index){
        Log.d(TAG, "Removing waypoint at index: " + index);
        try {
            waypointMissionBuilder.removeWaypoint(index);
            Log.d(TAG, "Waypoint removed successfully, remaining waypoints: " + waypointMissionBuilder.getWaypointCount());
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove waypoint at index: " + index, e);
        }
    }

    public void RemoveWaypoint(double latitude,double longitude){
        Log.d(TAG, "Removing waypoint at latitude: " + latitude + ", longitude: " + longitude);
        WaypointV2 mWaypoint = new WaypointV2.Builder()
                .setAltitude(altitude)
                .setCoordinate(new LocationCoordinate2D(latitude, longitude))
                .build();
        try {
            waypointMissionBuilder.removeWaypoint(mWaypoint);
            Log.d(TAG, "Waypoint removed successfully, remaining waypoints: " + waypointMissionBuilder.getWaypointCount());
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove waypoint at coordinates", e);
        }
    }

    public void configWayPointMission(WaypointV2MissionOperator instance) {
        Log.d(TAG, "Configuring waypoint mission");
        if (waypointMissionBuilder == null) {
            Log.d(TAG, "waypointMissionBuilder was null, creating new instance");
            waypointMissionBuilder = new WaypointV2Mission.Builder();
        }
        
        Log.d(TAG, "Mission config: finishedAction=" + mFinishedAction + 
              ", firstMode=" + firstMode + 
              ", speed=" + mSpeed + 
              ", waypointCount=" + waypointMissionBuilder.getWaypointCount());
              
        waypointMissionBuilder.setFinishedAction(mFinishedAction)
                .setMissionID(new Random().nextInt(65535))
                .setGotoFirstWaypointMode(firstMode)
                .setMaxFlightSpeed(mSpeed)
                .setAutoFlightSpeed(mSpeed);

        instance.loadMission(waypointMissionBuilder.build(), new CommonCallbacks.CompletionCallback<DJIWaypointV2Error>() {
            @Override
            public void onResult(DJIWaypointV2Error error) {
                if (error == null) {
                    Log.i(TAG, "loadWaypoint succeeded");
                    setResultToToast("loadWaypoint succeeded");
                } else {
                    Log.e(TAG, "loadWaypoint failed: " + error.getDescription() + ", errorCode: " + error.getErrorCode());
                    setResultToToast("loadWaypoint failed " + error.getDescription());
                }
                canUploadMission = true;
                Log.d(TAG, "canUploadMission set to: " + canUploadMission);
            }
        });
    }

    public void uploadWayPointMission(WaypointV2MissionOperator instance) {
        Log.d(TAG, "Attempting to upload waypoint mission, canUploadMission: " + canUploadMission);
        if (!canUploadMission) {
            Log.w(TAG, "Cannot upload mission, prerequisite not met");
            Toast.makeText(mContext, "Can`t upload Mission", Toast.LENGTH_SHORT).show();
            return;
        }
        instance.uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    Log.i(TAG, "Mission uploaded successfully");
                    setResultToToast("Mission upload successfully!");
                    canStartMission = true;
                    Log.d(TAG, "canStartMission set to: " + canStartMission);
                } else {
                    Log.e(TAG, "Mission upload failed: " + error.getDescription() + ", errorCode: " + error.getErrorCode());
                    setResultToToast("Mission upload failed, error: " + error.getDescription());
                    canStartMission = false;
                    Log.d(TAG, "canStartMission set to: " + canStartMission);
                }
            }
        });
    }

    public void startWaypointMission(WaypointV2MissionOperator instance) {
        Log.d(TAG, "Attempting to start waypoint mission, canStartMission: " + canStartMission);
        if (!canStartMission) {
            Log.w(TAG, "Cannot start mission, prerequisite not met");
            debugLog("can`t start mission");
            return;
        }
        canStartMission = false;
        Log.d(TAG, "Setting canStartMission to false");
        instance.startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    Log.i(TAG, "Mission started successfully");
                } else {
                    Log.e(TAG, "Mission start failed: " + error.getDescription() + ", errorCode: " + error.getErrorCode());
                }
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }

    public void stopWaypointMission(WaypointV2MissionOperator instance) {
        Log.d(TAG, "Attempting to stop waypoint mission");
        instance.stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    Log.i(TAG, "Mission stopped successfully");
                } else {
                    Log.e(TAG, "Mission stop failed: " + error.getDescription() + ", errorCode: " + error.getErrorCode());
                }
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }
    //endregion

    private void debugLog(String log) {
        Log.i(TAG, log);
    }
}