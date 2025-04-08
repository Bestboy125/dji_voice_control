package com.dji.sdk.voice_control.internal.controller.flightcontrol.track;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;

import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.interfaces.ControlActivityCallback;
import com.dji.sdk.voice_control.internal.controller.utils.JsonUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dji.common.error.DJIError;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.mission.activetrack.ActiveTrackMission;
import dji.common.mission.activetrack.ActiveTrackMissionEvent;
import dji.common.mission.activetrack.ActiveTrackMode;
import dji.common.mission.activetrack.ActiveTrackTargetState;
import dji.common.mission.activetrack.ActiveTrackTrackingState;
import dji.sdk.flightcontroller.FlightController;

public class llm_active_track {

    private static final String TAG = "llm_active_track";
    private static final int MAX_SEARCH_ATTEMPTS = 30;
    private static final int SLEEP_BETWEEN_SEARCH_MS = 3000;
    private static final String IMAGE_FILE_NAME = "active_track_frame.jpg";
    private static final int TRACKING_INFO_RECORD_INTERVAL_MS = 1000; // 1 second

    // Components
    private FlightController mFlightController;
    private CommandInterpreter mCI;
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    private TextureView mfpvTexture;
    private ControlActivityCallback callback;
    private ActiveTrack mActiveTrack;
    
    // Thread management
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Timer trackingRecordTimer;
    
    // Tracking state
    private boolean isTracking = false;
    private List<String> trackingRecords = new ArrayList<>();
    private String targetFeatureDescription;
    private RectF detectedRect;
    private float trackingConfidenceThreshold = 0.85f;

    /**
     * Callback for ActiveTrack events
     */
    private final ActiveTrack.ActiveTrackCallback activeTrackCallback = new ActiveTrack.ActiveTrackCallback() {
        @Override
        public void onActiveTrackEvent(ActiveTrackMissionEvent event) {
            // Log event and update tracking state
            Log.d(TAG, "Active track event: " + event.getCurrentState());
        }

        @Override
        public void onStatusUpdate(String statusMessage) {
            // Forward status updates to the UI
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "追踪状态: " + statusMessage));
        }

        @Override
        public void onError(String errorMessage) {
            // Handle errors
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "追踪错误: " + errorMessage));
        }
    };

    /**
     * Constructor for the llm_active_track class
     */
    public llm_active_track(
            CommandInterpreter commandInterpreter,
            FlightController flightController, 
            TextureView textureView,
            ControlActivityCallback callback
    ) {
        this.mCI = commandInterpreter;
        this.mFlightController = flightController;
        this.mfpvTexture = textureView;
        this.callback = callback;
        
        // Initialize ActiveTrack with callback
        this.mActiveTrack = new ActiveTrack(activeTrackCallback);
    }

    /**
     * Main entry point to start the active tracking process with a specified target feature
     * @param featureDescription Description of the target feature to track (e.g., "red car", "person with blue shirt")
     */
    public void startActiveTrackSearch(String featureDescription) {
        this.targetFeatureDescription = featureDescription;
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "开始搜索并追踪目标: " + featureDescription));
        
        // Start searching in a new thread
        executorService.execute(() -> {
            // Initialize virtual stick if needed
            // Get context from the callback's cache dir
            Context context = callback.getContext();
            mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
            
            // Take off if drone is not flying
            if (!callback.getisFlying()) {
                runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "起飞中..."));
                mCI.mTakeoff();
                sleep(5000); // Wait for takeoff
            }
            
            // Fly to an appropriate height for searching
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "调整到合适的搜索高度..."));
            mSingletonVirtualStickExecutor.mUp(5);
            sleep(3000);
            
            // Start the search process
            try {
                performSearch(1, MAX_SEARCH_ATTEMPTS);
            } catch (Exception e) {
                runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "搜索过程中发生错误: " + e.getMessage()));
                Log.e(TAG, "Error during search: ", e);
            }
        });
    }

    /**
     * Recursively perform the search for the target, with attempt counting
     */
    private void performSearch(int currentAttempt, int maxAttempts) {
        if (currentAttempt > maxAttempts) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "达到最大搜索次数，未找到目标"));
            return;
        }
        
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "搜索尝试 " + currentAttempt + "/" + maxAttempts));
        
        // Capture image and analyze
        boolean targetFound = doSearch();
        
        if (targetFound) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "目标已找到，开始追踪"));
            startTracking();
        } else {
            // Move to a new position and try again
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "未找到目标，移动到新位置继续搜索"));
            mSingletonVirtualStickExecutor.mTurn(25, 20); // Rotate by 25 degrees
            sleep(SLEEP_BETWEEN_SEARCH_MS);
            performSearch(currentAttempt + 1, maxAttempts);
        }
    }

    /**
     * Perform a single search attempt by taking a photo and analyzing it with GPT-4o
     * @return true if target is found, false otherwise
     */
    private boolean doSearch() {
        File imageFile = captureImage();
        if (imageFile == null) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "图像捕获失败"));
            return false;
        }
        
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        runOnUiThread(() -> {
            callback.addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");
            callback.addChatMessage(Constant.OWNER_HUMAN, bitmap);
        });
        
        // Prepare prompt for GPT-4o
        String prompt = createObjectDetectionPrompt(targetFeatureDescription);
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "请求GPT-4o分析图像中的目标..."));
        
        try {
            // Send to GPT-4o for analysis
            String gptResult = callback.sendQuestionToGPTSync(prompt, imageFile, true);
            JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(gptResult);
            String response = parseResult.getInferenceProcess();
            
            if (parseResult.getJsonData() == null) {
                runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "模型返回为空或格式错误，无法解析结果"));
                return false;
            }
            
            boolean targetFound = processGptResult(parseResult.getJsonData());
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, response));
            
            return targetFound;
        } catch (Exception e) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "分析过程中出错: " + e.getMessage()));
            Log.e(TAG, "Error analyzing image: ", e);
            return false;
        }
    }

    /**
     * Process the JSON result from GPT-4o to determine if target is found
     * @param jsonData JSON data from GPT-4o
     * @return true if target is found with sufficient confidence
     */
    private boolean processGptResult(JSONObject jsonData) {
        try {
            boolean objectFound = jsonData.optBoolean("object_found", false);
            double confidence = jsonData.optDouble("confidence", 0.0);
            
            if (objectFound && confidence >= trackingConfidenceThreshold) {
                // Extract bounding box coordinates
                JSONObject bbox = jsonData.optJSONObject("bounding_box");
                if (bbox != null) {
                    float left = (float) bbox.optDouble("left", 0.0);
                    float top = (float) bbox.optDouble("top", 0.0);
                    float right = (float) bbox.optDouble("right", 0.0);
                    float bottom = (float) bbox.optDouble("bottom", 0.0);
                    
                    // Create RectF from normalized coordinates (0.0-1.0)
                    detectedRect = new RectF(left, top, right, bottom);
                    
                    runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, 
                            String.format("目标已检测到! 置信度: %.2f%%, 位置: [%.2f, %.2f, %.2f, %.2f]", 
                                    confidence * 100, left, top, right, bottom)));
                    return true;
                }
            }
            
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, 
                    String.format("未检测到目标或置信度不足 (%.2f%%)", confidence * 100)));
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error processing GPT result: ", e);
            return false;
        }
    }

    /**
     * Start the ActiveTrack mission with the detected rectangle
     */
    private void startTracking() {
        if (detectedRect == null) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "无法开始追踪：未找到有效的目标区域"));
            return;
        }
        
        // Configure tracking parameters
        mActiveTrack.setTrackingMode(ActiveTrackMode.TRACE); // Use TRACE mode for following
        mActiveTrack.setRecommendedConfiguration();
        
        // Start tracking with the detected rectangle
        mActiveTrack.startTracking(detectedRect);
        isTracking = true;
        
        // Start recording tracking information
        startTrackingInfoRecording();
        
        // Process confirmation of tracking start
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "已开始追踪目标，可使用语音命令控制"));
    }
    
    /**
     * Start recording tracking information at regular intervals
     */
    private void startTrackingInfoRecording() {
        if (trackingRecordTimer != null) {
            trackingRecordTimer.cancel();
        }
        
        trackingRecordTimer = new Timer();
        trackingRecordTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                recordTrackingInfo();
            }
        }, 0, TRACKING_INFO_RECORD_INTERVAL_MS);
    }
    
    /**
     * Record current tracking information
     */
    private void recordTrackingInfo() {
        if (!isTracking) return;
        
        // Get current time
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        
        // Get drone location
        LocationCoordinate3D droneLocation = callback.getDroneLocation();
        
        // Get tracking info
        String trackingInfo = mActiveTrack.getTrackingTargetInfo();
        
        // Create record entry
        String record = String.format(
                "Time: %s, DronePos: [%.6f, %.6f, %.2f], Heading: %.2f, %s",
                timestamp,
                droneLocation.getLatitude(),
                droneLocation.getLongitude(),
                droneLocation.getAltitude(),
                callback.getHeading(),
                trackingInfo
        );
        
        // Add to records
        trackingRecords.add(record);
        
        // Log for debugging
        Log.d(TAG, "Tracking record: " + record);
    }
    
    /**
     * Stop the active tracking process
     */
    public void stopTracking() {
        if (!isTracking) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "当前未在追踪状态"));
            return;
        }
        
        // Stop the ActiveTrack mission
        mActiveTrack.stopTracking();
        isTracking = false;
        
        // Stop recording
        if (trackingRecordTimer != null) {
            trackingRecordTimer.cancel();
            trackingRecordTimer = null;
        }
        
        // Save tracking records
        saveTrackingRecords();
        
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "已停止追踪并保存记录"));
    }
    
    /**
     * Save the tracking records to a file
     */
    private void saveTrackingRecords() {
        try {
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "activetrack_log_" + timestamp + ".txt";
            
            // Create file
            File file = new File(callback.mgetCacheDir(), filename);
            FileOutputStream fos = new FileOutputStream(file);
            
            // Write header
            String header = "Active Tracking Log for target: " + targetFeatureDescription + "\n";
            header += "Start time: " + timestamp + "\n";
            header += "============================================================\n\n";
            fos.write(header.getBytes());
            
            // Write records
            for (String record : trackingRecords) {
                fos.write((record + "\n").getBytes());
            }
            
            fos.close();
            
            // Clear records after saving
            trackingRecords.clear();
            
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "追踪记录已保存到: " + file.getAbsolutePath()));
        } catch (Exception e) {
            Log.e(TAG, "Error saving tracking records: ", e);
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "保存追踪记录时出错: " + e.getMessage()));
        }
    }
    
    /**
     * Accept confirmation for tracking if in waiting state
     */
    public void acceptConfirmation() {
        if (!isTracking) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "当前未在追踪状态"));
            return;
        }
        
        mActiveTrack.acceptConfirmation();
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "已确认追踪"));
    }
    
    /**
     * Reject confirmation for tracking if in waiting state
     */
    public void rejectConfirmation() {
        if (!isTracking) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "当前未在追踪状态"));
            return;
        }
        
        mActiveTrack.rejectConfirmation();
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "已拒绝追踪"));
    }
    
    /**
     * Enable auto sensing for active tracking
     */
    public void enableAutoSensing() {
        mActiveTrack.enableAutoSensing();
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "已启用自动感知"));
    }
    
    /**
     * Disable auto sensing for active tracking
     */
    public void disableAutoSensing() {
        mActiveTrack.disableAutoSensing();
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "已禁用自动感知"));
    }

    /**
     * Create a prompt for object detection using GPT-4o
     */
    private String createObjectDetectionPrompt(String featureDescription) {
        return "请分析图像，回答以下问题。首先，详细描述您如何寻找特定目标的推理过程。然后，将您的答案以JSON格式输出。\n\n" +
                "推理过程：\n" +
                "- 描述您如何在图像中寻找和识别这个目标：" + featureDescription + "\n" +
                "- 解释您如何确定目标的位置和边界框\n" +
                "- 分析您对结果的置信度\n\n" +
                "请在推理过程之后，输出JSON格式的答案：\n\n" +
                "{\n" +
                "  \"object_found\": 布尔值（true或false），\n" +
                "  \"confidence\": 浮点数，范围0.0-1.0，表示您识别到目标的置信度，\n" +
                "  \"description\": \"字符串，描述目标的特征\",\n" +
                "  \"bounding_box\": {\n" +
                "    \"left\": 浮点数，范围0.0-1.0，表示边界框左边缘的归一化坐标,\n" +
                "    \"top\": 浮点数，范围0.0-1.0，表示边界框上边缘的归一化坐标,\n" +
                "    \"right\": 浮点数，范围0.0-1.0，表示边界框右边缘的归一化坐标,\n" +
                "    \"bottom\": 浮点数，范围0.0-1.0，表示边界框下边缘的归一化坐标\n" +
                "  }\n" +
                "}\n\n" +
                "**注意：**\n" +
                "- 请先输出推理过程，然后在下一行输出JSON对象。\n" +
                "- JSON格式必须严格遵循以上结构。\n" +
                "- 边界框坐标必须是归一化的值，即在0.0到1.0之间。\n" +
                "- 如果未找到目标，bounding_box可以为null。";
    }

    /**
     * Capture an image from the TextureView
     */
    private File captureImage() {
        Bitmap bitmap = mfpvTexture.getBitmap();
        if (bitmap == null) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView可能未准备好"));
            return null;
        }

        File imageFile = saveBitmapAsFile(bitmap, IMAGE_FILE_NAME);
        if (imageFile == null) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "图片保存失败"));
            return null;
        }
        return imageFile;
    }

    /**
     * Save a bitmap as a file
     */
    private File saveBitmapAsFile(Bitmap bitmap, String filename) {
        File file = new File(callback.mgetCacheDir(), filename);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (IOException e) {
            Log.e(TAG, "Error saving bitmap: ", e);
            return null;
        }
        return file;
    }

    /**
     * Sleep for a specified duration
     */
    private void sleep(int timeMs) {
        try {
            Thread.sleep(timeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Thread interrupted", e);
        }
    }

    /**
     * Run a Runnable on the UI thread
     */
    private void runOnUiThread(Runnable runnable) {
        mainHandler.post(runnable);
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (isTracking) {
            stopTracking();
        }
        
        if (trackingRecordTimer != null) {
            trackingRecordTimer.cancel();
            trackingRecordTimer = null;
        }
        
        if (mActiveTrack != null) {
            mActiveTrack.cleanup();
        }
        
        executorService.shutdown();
    }
} 