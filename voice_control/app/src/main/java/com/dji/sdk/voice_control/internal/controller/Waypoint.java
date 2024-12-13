package com.dji.sdk.voice_control.internal.controller;

import static com.dji.sdk.voice_control.internal.utils.ToastUtils.setResultToToast;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.error.DJIError;
import dji.common.error.DJIWaypointV2Error;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.RTKState;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypointv2.Action.ActionDownloadEvent;
import dji.common.mission.waypointv2.Action.ActionExecutionEvent;
import dji.common.mission.waypointv2.Action.ActionState;
import dji.common.mission.waypointv2.Action.ActionUploadEvent;
import dji.common.mission.waypointv2.Action.WaypointV2Action;
import dji.common.mission.waypointv2.WaypointV2;
import dji.common.mission.waypointv2.WaypointV2Mission;
import dji.common.mission.waypointv2.WaypointV2MissionDownloadEvent;
import dji.common.mission.waypointv2.WaypointV2MissionExecutionEvent;
import dji.common.mission.waypointv2.WaypointV2MissionState;
import dji.common.mission.waypointv2.WaypointV2MissionTypes;
import dji.common.mission.waypointv2.WaypointV2MissionUploadEvent;
import dji.common.model.LocationCoordinate2D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.RTK;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointV2ActionListener;
import dji.sdk.mission.waypoint.WaypointV2MissionOperator;
import dji.sdk.mission.waypoint.WaypointV2MissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

import com.amap.api.maps2d.model.Marker;
import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.controller.waypoint.WaypointV2ActionDialog;

public class Waypoint {

    private View view;
    private Context mContext;

    Waypoint(View v){
        this.view = v;
    }

    Waypoint(Context mContext){
        this.mContext = mContext;
    }

    //region 数据结构
    private boolean isAdd = false;

    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    private List<WaypointV2> waypointList = new ArrayList<>();

    public static WaypointV2Mission.Builder waypointMissionBuilder;

    private FlightController mFlightController;
    private WaypointV2MissionTypes.MissionFinishedAction mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    private WaypointV2MissionTypes.MissionGotoWaypointMode firstMode = WaypointV2MissionTypes.MissionGotoWaypointMode.SAFELY;
    private WaypointV2ActionDialog mActionDialog;
    private List<WaypointV2Action> v2Actions;
    private boolean canUploadAction;
    private boolean canUploadMission;
    private boolean canStartMission;
    private boolean ifNeedUploadAction;
    private double mHomeLat = 181;
    private double mHomeLng = 181;
    private double mAircraftLat = 181;
    private double mAircraftLng = 181;
    private boolean useRTKLocation = false;
    private RTK mRtk;
    private float droneHeading;
    private float droneHeight;
    //endregion

    public int getWaypointCount(WaypointV2MissionOperator instance){
        return waypointMissionBuilder.getWaypointCount();
    }

    //region 航点操作
    public WaypointV2MissionOperator getWaypointMissionOperator(WaypointV2MissionOperator instance) {
        if (instance == null) {
            MissionControl missionControl = DJISDKManager.getInstance().getMissionControl();
            if (missionControl != null) {
                instance = missionControl.getWaypointMissionV2Operator();
            }
        }
        return instance;
    }

    public void AddWaypoint(double latitude,double longitude) {
        WaypointV2 mWaypoint = new WaypointV2.Builder()
                .setAltitude(altitude)
                .setCoordinate(new LocationCoordinate2D(latitude, longitude))
                .build();
        //Add Waypoints to Waypoint arraylist;
        if (waypointMissionBuilder == null) {
            waypointMissionBuilder = new WaypointV2Mission.Builder();
        }
        waypointMissionBuilder.addWaypoint(mWaypoint);
    }

    public void RemoveWaypoint(int index){
        waypointMissionBuilder.removeWaypoint(index);
    }

    public void RemoveWaypoint(double latitude,double longitude){
        WaypointV2 mWaypoint = new WaypointV2.Builder()
                .setAltitude(altitude)
                .setCoordinate(new LocationCoordinate2D(latitude, longitude))
                .build();
        waypointMissionBuilder.removeWaypoint(mWaypoint);

    }

    public void configWayPointMission(WaypointV2MissionOperator instance) {

        if (waypointMissionBuilder == null) {
//            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
//                    .headingMode(mHeadingMode)
//                    .autoFlightSpeed(mSpeed)
//                    .maxFlightSpeed(mSpeed)
//                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
            waypointMissionBuilder = new WaypointV2Mission.Builder();

        }
        waypointMissionBuilder.setFinishedAction(mFinishedAction)
                .setMissionID(new Random().nextInt(65535))
                .setFinishedAction(mFinishedAction)
                .setGotoFirstWaypointMode(firstMode)
                .setMaxFlightSpeed(mSpeed)
                .setAutoFlightSpeed(mSpeed);

        instance.loadMission(waypointMissionBuilder.build(), new CommonCallbacks.CompletionCallback<DJIWaypointV2Error>() {
            @Override
            public void onResult(DJIWaypointV2Error error) {
                if (error == null) {
                    setResultToToast("loadWaypoint succeeded");
                } else {
                    setResultToToast("loadWaypoint failed " + error.getDescription());
                }
                canUploadMission = true;
            }
        });


    }

    public void uploadWayPointMission(WaypointV2MissionOperator instance) {

        if (!canUploadMission) {
            Toast.makeText(mContext, "Can`t upload Mission", Toast.LENGTH_SHORT).show();
            return;
        }
        instance.uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription());
                }
            }
        });

    }

    public void startWaypointMission(WaypointV2MissionOperator instance) {
        if (!canStartMission) {
            debugLog("can`t start mission");
            return;
        }
        canStartMission = false;
        instance.startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    public void stopWaypointMission(WaypointV2MissionOperator instance) {

        instance.stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }
    //endregion

    private void debugLog(String log) {
        Log.i("WP2.0", log);
    }
}