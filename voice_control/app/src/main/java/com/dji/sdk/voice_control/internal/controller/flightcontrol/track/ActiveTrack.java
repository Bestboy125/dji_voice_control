package com.dji.sdk.voice_control.internal.controller.flightcontrol.track;

import android.graphics.RectF;
import androidx.annotation.NonNull;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.mission.activetrack.ActiveTrackMission;
import dji.common.mission.activetrack.ActiveTrackMissionEvent;
import dji.common.mission.activetrack.ActiveTrackMode;
import dji.common.mission.activetrack.ActiveTrackState;
import dji.common.mission.activetrack.ActiveTrackTargetState;
import dji.common.mission.activetrack.ActiveTrackTrackingState;
import dji.common.mission.activetrack.QuickShotMode;
import dji.common.mission.activetrack.SubjectSensingState;
import dji.common.util.CommonCallbacks.CompletionCallback;
import dji.common.util.CommonCallbacks.CompletionCallbackWith;
import dji.keysdk.CameraKey;
import dji.keysdk.DJIKey;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.keysdk.callback.ActionCallback;
import dji.keysdk.callback.SetCallback;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.activetrack.ActiveTrackMissionOperatorListener;
import dji.sdk.mission.activetrack.ActiveTrackOperator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveTrack implements ActiveTrackMissionOperatorListener {

    private static final String TAG = "ActiveTrack";
    private static final int MAIN_CAMERA_INDEX = 0;
    private static final int INVALID_INDEX = -1;

    private ActiveTrackOperator mActiveTrackOperator;
    private ActiveTrackMission mActiveTrackMission;
    private final DJIKey trackModeKey = FlightControllerKey.createFlightAssistantKey(FlightControllerKey.ACTIVE_TRACK_MODE);
    private int trackingIndex = INVALID_INDEX;
    private boolean isAutoSensingSupported = false;
    private ActiveTrackMode startMode = ActiveTrackMode.TRACE;
    private QuickShotMode quickShotMode = QuickShotMode.UNKNOWN;
    private ActiveTrackCallback mCallback;
    private String trackingInfo = "";

    /**
     * Callback interface for ActiveTrack events
     */
    public interface ActiveTrackCallback {
        void onActiveTrackEvent(ActiveTrackMissionEvent event);
        void onStatusUpdate(String statusMessage);
        void onError(String errorMessage);
    }

    public ActiveTrack(ActiveTrackCallback callback) {
        this.mCallback = callback;
        initMissionManager();
    }

    // Helper class for string formatting
    private static class Utils {
        public static void addLineToSB(StringBuffer sb, String label, Object value) {
            sb.append(label).append(value).append("\n");
        }
    }

    /**
     * Get the current tracking target information
     * 
     * @return String containing detailed tracking information
     */
    public String getTrackingTargetInfo() {
        return trackingInfo;
    }

    /**
     * Initialize the ActiveTrack mission manager
     */
    public void initMissionManager() {
        mActiveTrackOperator = MissionControl.getInstance().getActiveTrackOperator();
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("Failed to get ActiveTrackOperator");
            }
            return;
        }

        mActiveTrackOperator.addListener(this);
    }

    /**
     * Sets the recommended configuration for active tracking
     */
    public void setRecommendedConfiguration() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        mActiveTrackOperator.setRecommendedConfiguration(new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Set Recommended Config Success");
                    } else {
                        mCallback.onError("Set Recommended Config Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Starts tracking with the provided rectangle
     * 
     * @param rectF The rectangle defining the tracking area
     */
    public void startTracking(RectF rectF) {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        mActiveTrackMission = new ActiveTrackMission(rectF, startMode);
        if (startMode == ActiveTrackMode.QUICK_SHOT) {
            mActiveTrackMission.setQuickShotMode(quickShotMode);
            checkStorageStates();
        }
        
        mActiveTrackOperator.startTracking(mActiveTrackMission, new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Start Tracking Success");
                    } else {
                        mCallback.onError("Start Tracking Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Starts auto sensing mission with the current target index
     */
    public void startAutoSensingMission() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        if (trackingIndex != INVALID_INDEX) {
            ActiveTrackMission mission = new ActiveTrackMission(null, startMode);
            mission.setQuickShotMode(quickShotMode);
            mission.setTargetIndex(trackingIndex);
            
            mActiveTrackOperator.startAutoSensingMission(mission, new CompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    if (mCallback != null) {
                        if (error == null) {
                            mCallback.onStatusUpdate("Accept Confirm index: " + trackingIndex + " Success");
                            trackingIndex = INVALID_INDEX;
                        } else {
                            mCallback.onError(error.getDescription());
                        }
                    }
                }
            });
        } else if (mCallback != null) {
            mCallback.onError("Invalid tracking index");
        }
    }

    /**
     * Stops the current tracking mission
     */
    public void stopTracking() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        trackingIndex = INVALID_INDEX;
        mActiveTrackOperator.stopTracking(new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Stop Tracking Success");
                    } else {
                        mCallback.onError("Stop Tracking Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Accepts the confirmation for the current tracking target
     */
    public void acceptConfirmation() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        trackingIndex = INVALID_INDEX;
        mActiveTrackOperator.acceptConfirmation(new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Accept Confirmation Success");
                    } else {
                        mCallback.onError("Accept Confirmation Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Rejects the confirmation for the current tracking target
     */
    public void rejectConfirmation() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        trackingIndex = INVALID_INDEX;
        mActiveTrackOperator.rejectConfirmation(new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Reject Confirmation Success");
                    } else {
                        mCallback.onError("Reject Confirmation Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Sets the retreat enabled setting
     * 
     * @param enabled Whether retreat is enabled
     */
    public void setRetreatEnabled(boolean enabled) {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        mActiveTrackOperator.setRetreatEnabled(enabled, new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Set Retreat Enabled: " + enabled);
                    } else {
                        mCallback.onError("Set Retreat Enabled Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Gets the retreat enabled setting
     */
    public void getRetreatEnabled() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        mActiveTrackOperator.getRetreatEnabled(new CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(final Boolean enabled) {
                if (mCallback != null) {
                    mCallback.onStatusUpdate("Retreat Enabled: " + enabled);
                }
            }

            @Override
            public void onFailure(DJIError error) {
                if (mCallback != null) {
                    mCallback.onError("Get Retreat Enabled Failed: " + error.getDescription());
                }
            }
        });
    }

    /**
     * Sets the gesture mode enabled setting
     * 
     * @param enabled Whether gesture mode is enabled
     */
    public void setGestureModeEnabled(boolean enabled) {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        mActiveTrackOperator.setGestureModeEnabled(enabled, new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Set GestureMode Enabled: " + enabled);
                    } else {
                        mCallback.onError("Set GestureMode Enabled Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Enables auto sensing for active tracking
     */
    public void enableAutoSensing() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        startMode = ActiveTrackMode.TRACE;
        mActiveTrackOperator.enableAutoSensing(new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Set AutoSensing Enabled Success");
                    } else {
                        mCallback.onError("Set AutoSensing Enabled Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Enables auto sensing for QuickShot
     */
    public void enableAutoSensingForQuickShot() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        mActiveTrackOperator.enableAutoSensingForQuickShot(new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Set QuickShot Enabled Success");
                    } else {
                        mCallback.onError("Set QuickShot Enabled Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Disables auto sensing
     */
    public void disableAutoSensing() {
        if (mActiveTrackOperator == null) {
            if (mCallback != null) {
                mCallback.onError("ActiveTrackOperator is null");
            }
            return;
        }
        
        mActiveTrackOperator.disableAutoSensing(new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (mCallback != null) {
                    if (error == null) {
                        mCallback.onStatusUpdate("Disable Auto Sensing Success");
                        isAutoSensingSupported = false;
                    } else {
                        mCallback.onError("Disable Auto Sensing Failed: " + error.getDescription());
                    }
                }
            }
        });
    }

    /**
     * Sets the tracking mode
     * 
     * @param mode The tracking mode to set
     */
    public void setTrackingMode(ActiveTrackMode mode) {
        this.startMode = mode;
    }

    /**
     * Sets the QuickShot mode
     * 
     * @param mode The QuickShot mode to set
     */
    public void setQuickShotMode(QuickShotMode mode) {
        this.quickShotMode = mode;
    }

    /**
     * Sets the tracking index
     * 
     * @param index The tracking index to set
     */
    public void setTrackingIndex(int index) {
        this.trackingIndex = index;
    }

    /**
     * Gets whether auto sensing is supported
     * 
     * @return Whether auto sensing is supported
     */
    public boolean isAutoSensingSupported() {
        return isAutoSensingSupported;
    }

    /**
     * Checks and ensures proper storage state for recording
     */
    private void checkStorageStates() {
        KeyManager keyManager = KeyManager.getInstance();
        DJIKey storageLocationkey = CameraKey.create(CameraKey.CAMERA_STORAGE_LOCATION, MAIN_CAMERA_INDEX);
        Object storageLocationObj = keyManager.getValue(storageLocationkey);
        SettingsDefinitions.StorageLocation storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;

        if (storageLocationObj instanceof SettingsDefinitions.StorageLocation) {
            storageLocation = (SettingsDefinitions.StorageLocation) storageLocationObj;
        }

        if (storageLocation == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) {
            if (!isInternalStorageReady(MAIN_CAMERA_INDEX) && isSDCardReady(MAIN_CAMERA_INDEX)) {
                switchStorageLocation(SettingsDefinitions.StorageLocation.SDCARD);
            }
        }

        if (storageLocation == SettingsDefinitions.StorageLocation.SDCARD) {
            if (!isSDCardReady(MAIN_CAMERA_INDEX) && isInternalStorageReady(MAIN_CAMERA_INDEX)) {
                switchStorageLocation(SettingsDefinitions.StorageLocation.INTERNAL_STORAGE);
            }
        }

        DJIKey isRecordingKey = CameraKey.create(CameraKey.IS_RECORDING, MAIN_CAMERA_INDEX);
        Object isRecording = keyManager.getValue(isRecordingKey);
        if (isRecording instanceof Boolean) {
            if (((Boolean) isRecording).booleanValue()) {
                keyManager.performAction(CameraKey.create(CameraKey.STOP_RECORD_VIDEO, MAIN_CAMERA_INDEX), new ActionCallback() {
                    @Override
                    public void onSuccess() {
                        if (mCallback != null) {
                            mCallback.onStatusUpdate("Stop Recording Success");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull DJIError error) {
                        if (mCallback != null) {
                            mCallback.onError("Stop Recording Failed: " + error.getDescription());
                        }
                    }
                });
            }
        }
    }

    /**
     * Checks if the SD card is ready
     * 
     * @param index Camera index
     * @return Whether the SD card is ready
     */
    private boolean isSDCardReady(int index) {
        KeyManager keyManager = KeyManager.getInstance();

        return ((Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_INSERTED, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_INITIALIZING, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_READ_ONLY, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_HAS_ERROR, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_FULL, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_BUSY, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_FORMATTING, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_INVALID_FORMAT, index))
                && (Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_VERIFIED, index))
                && (Long) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_AVAILABLE_CAPTURE_COUNT, index)) > 0L
                && (Integer) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_AVAILABLE_RECORDING_TIME_IN_SECONDS, index)) > 0);
    }

    /**
     * Checks if the internal storage is ready
     * 
     * @param index Camera index
     * @return Whether the internal storage is ready
     */
    private boolean isInternalStorageReady(int index) {
        KeyManager keyManager = KeyManager.getInstance();

        boolean isInternalSupported = (boolean)
                keyManager.getValue(CameraKey.create(CameraKey.IS_INTERNAL_STORAGE_SUPPORTED, index));
        if (isInternalSupported) {
            return ((Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_INSERTED, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_INITIALIZING, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_READ_ONLY, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_HAS_ERROR, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_FULL, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_BUSY, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_FORMATTING, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_INVALID_FORMAT, index))
                    && (Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_VERIFIED, index))
                    && (Long) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_AVAILABLE_CAPTURE_COUNT, index)) > 0L
                    && (Integer) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_AVAILABLE_RECORDING_TIME_IN_SECONDS, index)) > 0);
        }
        return false;
    }

    /**
     * Switches the storage location
     * 
     * @param storageLocation The storage location to switch to
     */
    private void switchStorageLocation(final SettingsDefinitions.StorageLocation storageLocation) {
        KeyManager keyManager = KeyManager.getInstance();
        DJIKey storageLocationKey = CameraKey.create(CameraKey.CAMERA_STORAGE_LOCATION, MAIN_CAMERA_INDEX);

        if (storageLocation == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) {
            keyManager.setValue(storageLocationKey, SettingsDefinitions.StorageLocation.SDCARD, new SetCallback() {
                @Override
                public void onSuccess() {
                    if (mCallback != null) {
                        mCallback.onStatusUpdate("Changed to SD card");
                    }
                }

                @Override
                public void onFailure(@NonNull DJIError error) {
                    if (mCallback != null) {
                        mCallback.onError(error.getDescription());
                    }
                }
            });
        } else {
            keyManager.setValue(storageLocationKey, SettingsDefinitions.StorageLocation.INTERNAL_STORAGE, new SetCallback() {
                @Override
                public void onSuccess() {
                    if (mCallback != null) {
                        mCallback.onStatusUpdate("Changed to Internal Storage");
                    }
                }

                @Override
                public void onFailure(@NonNull DJIError error) {
                    if (mCallback != null) {
                        mCallback.onError(error.getDescription());
                    }
                }
            });
        }
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (mActiveTrackOperator != null) {
            mActiveTrackOperator.removeListener(this);
        }
    }

    @Override
    public void onUpdate(ActiveTrackMissionEvent event) {
        if (mCallback != null) {
            mCallback.onActiveTrackEvent(event);
        }
        
        // Process and store tracking information
        StringBuffer sb = new StringBuffer();
        String errorInformation = (event.getError() == null ? "null" : event.getError().getDescription()) + "\n";
        String currentState = event.getCurrentState() == null ? "null" : event.getCurrentState().getName();
        String previousState = event.getPreviousState() == null ? "null" : event.getPreviousState().getName();

        ActiveTrackTargetState targetState = ActiveTrackTargetState.UNKNOWN;
        if (event.getTrackingState() != null) {
            targetState = event.getTrackingState().getState();
        }
        Utils.addLineToSB(sb, "CurrentState: ", currentState);
        Utils.addLineToSB(sb, "PreviousState: ", previousState);
        Utils.addLineToSB(sb, "TargetState: ", targetState);
        Utils.addLineToSB(sb, "Error:", errorInformation);

        Object value = KeyManager.getInstance().getValue(trackModeKey);
        if (value instanceof ActiveTrackMode) {
            Utils.addLineToSB(sb, "TrackingMode:", value.toString());
        }

        ActiveTrackTrackingState trackingState = event.getTrackingState();
        if (trackingState != null) {
            final SubjectSensingState[] targetSensingInformations = trackingState.getAutoSensedSubjects();
            if (targetSensingInformations != null) {
                for (SubjectSensingState subjectSensingState : targetSensingInformations) {
                    RectF trackingRect = subjectSensingState.getTargetRect();
                    if (trackingRect != null) {
                        Utils.addLineToSB(sb, "Rect center x: ", trackingRect.centerX());
                        Utils.addLineToSB(sb, "Rect center y: ", trackingRect.centerY());
                        Utils.addLineToSB(sb, "Rect Width: ", trackingRect.width());
                        Utils.addLineToSB(sb, "Rect Height: ", trackingRect.height());
                        Utils.addLineToSB(sb, "Reason", trackingState.getReason().name());
                        Utils.addLineToSB(sb, "Target Index: ", subjectSensingState.getIndex());
                        Utils.addLineToSB(sb, "Target Type", subjectSensingState.getTargetType().name());
                        Utils.addLineToSB(sb, "Target State", subjectSensingState.getState().name());
                        isAutoSensingSupported = true;
                    }
                }
            } else {
                RectF trackingRect = trackingState.getTargetRect();
                if (trackingRect != null) {
                    Utils.addLineToSB(sb, "Rect center x: ", trackingRect.centerX());
                    Utils.addLineToSB(sb, "Rect center y: ", trackingRect.centerY());
                    Utils.addLineToSB(sb, "Rect Width: ", trackingRect.width());
                    Utils.addLineToSB(sb, "Rect Height: ", trackingRect.height());
                    Utils.addLineToSB(sb, "Reason", trackingState.getReason().name());
                    Utils.addLineToSB(sb, "Target Index: ", trackingState.getTargetIndex());
                    Utils.addLineToSB(sb, "Target Type", trackingState.getType().name());
                    Utils.addLineToSB(sb, "Target State", trackingState.getState().name());
                    isAutoSensingSupported = false;
                }
            }
        }
        
        // Store the tracking information
        trackingInfo = sb.toString();
        
        // Notify callback about detailed tracking information
        if (mCallback != null) {
            mCallback.onStatusUpdate(trackingInfo);
        }
    }
}
