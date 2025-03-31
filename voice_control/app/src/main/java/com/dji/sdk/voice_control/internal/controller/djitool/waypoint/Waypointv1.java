package com.dji.sdk.voice_control.internal.controller.djitool.waypoint;

import static com.dji.sdk.voice_control.internal.djidemo.utils.ToastUtils.setResultToToast;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.Random;

import dji.common.error.DJIError;
import dji.common.error.DJIWaypointV2Error;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypointv2.WaypointV2;
import dji.common.mission.waypointv2.WaypointV2MissionTypes;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointV2MissionOperator;
import io.reactivex.annotations.NonNull;

public class Waypointv1 {

    private static final String TAG = "Waypoint";
    private View view;
    private Context mContext;

    Waypointv1(View v){
        this.view = v;
        Log.d(TAG, "Waypoint initialized with view");
    }

    public Waypointv1(){
        Log.d(TAG, "Waypoint initialized with default constructor");
    }

    //region 数据结构

    public float altitude = 5.0f;
    public float mSpeed = 3.0f;

    public static WaypointMission.Builder waypointMissionBuilder;
    public WaypointMissionOperator instance;
    public WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    public WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    public WaypointV2MissionTypes.MissionGotoWaypointMode firstMode = WaypointV2MissionTypes.MissionGotoWaypointMode.SAFELY;
    public WaypointMissionFlightPathMode waypointMissionFlightPathMode = WaypointMissionFlightPathMode.NORMAL;
    public boolean canUploadMission;
    public boolean canStartMission;
    //endregion

    public int getWaypointCount(WaypointMissionOperator instance){
        int count = waypointMissionBuilder.getWaypointCount();
        Log.d(TAG, "getWaypointCount: " + count);
        return count;
    }


    public void AddWaypoint(double latitude, double longitude, float altitude, int heading, float speed, float cornermeters) {
        Log.d(TAG, "Adding waypoint at latitude: " + latitude + ", longitude: " + longitude + ", altitude: " + altitude);
        Waypoint mWaypoint = new Waypoint(latitude,longitude,altitude);
        mWaypoint.heading =heading;
        mWaypoint.speed = speed;
        mWaypoint.cornerRadiusInMeters = cornermeters;
        //Add Waypoints to Waypoint arraylist;
        if (waypointMissionBuilder == null) {
            Log.d(TAG, "waypointMissionBuilder was null, creating new instance");
            waypointMissionBuilder = new WaypointMission.Builder();
        }
        waypointMissionBuilder.addWaypoint(mWaypoint);
        Log.d(TAG, "Waypoint added successfully, total waypoints: " + waypointMissionBuilder.getWaypointCount());
    }

    public void AddWaypoint(double latitude, double longitude, float altitude, float cornermeters) {
        Log.d(TAG, "Adding waypoint at latitude: " + latitude + ", longitude: " + longitude + ", altitude: " + altitude);
        Waypoint mWaypoint = new Waypoint(latitude,longitude,altitude);
        mWaypoint.cornerRadiusInMeters = cornermeters;
        //Add Waypoints to Waypoint arraylist;
        if (waypointMissionBuilder == null) {
            Log.d(TAG, "waypointMissionBuilder was null, creating new instance");
            waypointMissionBuilder = new WaypointMission.Builder();
        }
        waypointMissionBuilder.addWaypoint(mWaypoint);
        Log.d(TAG, "Waypoint added successfully, total waypoints: " + waypointMissionBuilder.getWaypointCount());
    }

    public void AddPointInterst(double lattitude, double longitude){
        if(waypointMissionBuilder == null){
            waypointMissionBuilder = new WaypointMission.Builder();
        }
        waypointMissionBuilder.setPointOfInterest(new LocationCoordinate2D(lattitude,longitude));
        Log.d(TAG,"设置兴趣点成功");
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
        Waypoint mWaypoint = new Waypoint(latitude,longitude,altitude);
        try {
            waypointMissionBuilder.removeWaypoint(mWaypoint);
            Log.d(TAG, "Waypoint removed successfully, remaining waypoints: " + waypointMissionBuilder.getWaypointCount());
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove waypoint at coordinates", e);
        }
    }

    public void configWayPointMission(WaypointMissionOperator instance) {
        Log.d(TAG, "Configuring waypoint mission");
        if (waypointMissionBuilder == null) {
            Log.d(TAG, "waypointMissionBuilder was null, creating new instance");
            waypointMissionBuilder = new WaypointMission.Builder();
        }
        
        Log.d(TAG, "Mission config: finishedAction=" + mFinishedAction + 
              ", firstMode=" + firstMode + 
              ", speed=" + mSpeed + 
              ", waypointCount=" + waypointMissionBuilder.getWaypointCount());

        waypointMissionBuilder.finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(mSpeed)
                .maxFlightSpeed(mSpeed)
                .flightPathMode(waypointMissionFlightPathMode);

        DJIError error = instance.loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
            canUploadMission = true;
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }
    }

    public void configCycleWayPointMission(WaypointMissionOperator instance) {
        Log.d(TAG, "Configuring waypoint mission");
        if (waypointMissionBuilder == null) {
            Log.d(TAG, "waypointMissionBuilder was null, creating new instance");
            waypointMissionBuilder = new WaypointMission.Builder();
        }

        Log.d(TAG, "Mission config: finishedAction=" + mFinishedAction +
                ", firstMode=" + firstMode +
                ", speed=" + mSpeed +
                ", waypointCount=" + waypointMissionBuilder.getWaypointCount());

        waypointMissionBuilder.finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(mSpeed)
                .maxFlightSpeed(mSpeed)
                .flightPathMode(waypointMissionFlightPathMode);

        DJIError error = instance.loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
            canUploadMission = true;
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }
    }

    public void uploadWayPointMission(WaypointMissionOperator instance) {
        Log.d(TAG, "Attempting to upload waypoint mission, canUploadMission: " + canUploadMission);
        if (!canUploadMission) {
            Log.w(TAG, "Cannot upload mission, prerequisite not met");
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

    public void startWaypointMission(WaypointMissionOperator instance) {
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

    public void stopWaypointMission(WaypointMissionOperator instance) {
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