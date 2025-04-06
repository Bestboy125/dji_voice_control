package com.dji.sdk.voice_control.internal.controller.flightcontrol.agent;

import static com.dji.sdk.voice_control.internal.controller.yolo.Constants.MODEL_PATH;
import static com.dji.sdk.voice_control.internal.controller.yolo.Constants.LABELS_PATH;
import static com.dji.sdk.voice_control.internal.djidemo.utils.ToastUtils.showToast;
import static com.dji.sdk.voice_control.internal.djidemo.utils.ToastUtils.setResultToToast;
import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;

import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.interfaces.ControlActivityCallback;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.djitool.gimbal.gimbalControl;
import com.dji.sdk.voice_control.internal.controller.djitool.waypoint.Waypointv1;
import com.dji.sdk.voice_control.internal.controller.utils.JsonUtils;
import com.dji.sdk.voice_control.internal.controller.utils.Utils;
import com.dji.sdk.voice_control.internal.prompt.TaskDecompositionPromptBuilder;
import com.dji.sdk.voice_control.internal.prompt.vlmPromptBuilder;
import com.dji.sdk.voice_control.internal.controller.yolo.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.gimbal.GimbalState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

// Mission related imports
import dji.common.mission.hotpoint.HotpointHeading;
import dji.common.mission.hotpoint.HotpointMission;
import dji.common.mission.hotpoint.HotpointMissionEvent;
import dji.common.mission.hotpoint.HotpointStartPoint;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.hotpoint.HotpointMissionOperator;
import dji.sdk.mission.hotpoint.HotpointMissionOperatorListener;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;

/**
 * TargetCollectionAgent - 目标信息收集代理
 * 提供完整的信息收集流程，包括任务分解、执行和状态管理
 */
public class TargetCollectionAgent {
    private static final String TAG = "TargetCollectionAgent";

    // 组件引用
    private FlightController mFlightController;
    private CommandInterpreter mCommandInterpreter;
    private MyVirtualStickExecutor mVirtualStickExecutor;
    private TextureView mTextureView;
    private ControlActivityCallback mCallback;
    private Context mContext;
    private gimbalControl mGimbalControl;

    // 任务状态
    private JSONObject mTaskDecomposition;
    private vlmPromptBuilder mPromptBuilder;
    private int mCurrentStep = 0;
    private String mCurrentHighLevelTask;
    private AtomicBoolean mIsTaskRunning = new AtomicBoolean(false);
    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    // 配置
    private static final int SLEEP_BETWEEN_STEPS_MS = 3000;
    private static final String IMAGE_FILE_NAME = "target_frame.jpg";
    private LocationCoordinate3D mInitialLocation; // 任务开始时的位置，用于返航
    
    // 航点任务相关
    private Waypointv1 mWaypoint;
    private WaypointMissionOperator mMissionOperator;
    private WaypointMissionOperatorListener eventNotificationListener;
    
    // 热点任务相关
    private HotpointMissionOperator hotpointMissionOperator = null;
    private HotpointMissionOperatorListener hotpointlistener;
    private boolean isend = false;
    private float reduis = 0f;
    
    // 目标和无人机位置
    private LocationCoordinate3D droneLocation = new LocationCoordinate3D(0,0,0);
    private double droneLon = 0.0;
    private double droneLat = 0.0;
    private double droneAlt = 0.0;
    private double objLon = 0.0;
    private double objLat = 0.0;
    private double objAlt = 0.0;

    /**
     * 构造函数
     */
    public TargetCollectionAgent(Context context,
                                 CommandInterpreter commandInterpreter,
                                 FlightController flightController,
                                 TextureView textureView,
                                 ControlActivityCallback callback) {
        this.mContext = context;
        this.mCommandInterpreter = commandInterpreter;
        this.mFlightController = flightController;
        this.mTextureView = textureView;
        this.mCallback = callback;
        this.mVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
        this.mGimbalControl = new gimbalControl();
    }

    /**
     * 初始化并启动任务流程
     */
    public void startMission() {
        if (mIsTaskRunning.get()) {
            runOnUiThread(() -> {
                mCallback.addChatMessage(Constant.OWNER_BOT, "任务已在运行中，请等待当前任务完成");
            });
            return;
        }

        mIsTaskRunning.set(true);
        mExecutorService.execute(() -> {
            try {
                // 1. 记录初始位置
                captureInitialPosition();

                // 2. 构建任务分解提示
                String taskDecompositionPrompt = buildTaskDecompositionPrompt();
                logAndShowMessage("任务分解提示构建完成，准备发送至LLM...");

                // 3. 发送任务分解请求到LLM并解析结果
                String taskDecompositionResult = mCallback.sendQuestionToGPTSync(taskDecompositionPrompt, null, false);
                mTaskDecomposition = parseTaskDecomposition(taskDecompositionResult);
                logAndDisplayTaskDecomposition();

                // 4. 初始化VLM提示构建器
                initializeVlmPromptBuilder();

                // 5. 开始执行任务流程
                executeTaskFlow();

            } catch (Exception e) {
                logAndShowMessage("任务执行出错: " + e.getMessage());
                e.printStackTrace();
                mIsTaskRunning.set(false);
            }
        });
    }

    /**
     * 捕获初始位置用于最后的返航
     */
    private void captureInitialPosition() {
        if (mFlightController != null) {
            mInitialLocation = mFlightController.getState().getAircraftLocation();
            logAndShowMessage("已记录初始位置: 经度=" + mInitialLocation.getLongitude()
                    + ", 纬度=" + mInitialLocation.getLatitude()
                    + ", 高度=" + mInitialLocation.getAltitude());
        } else {
            logAndShowMessage("警告: 无法获取初始位置");
        }
    }

    /**
     * 构建任务分解提示
     */
    private String buildTaskDecompositionPrompt() {
        TaskDecompositionPromptBuilder promptBuilder = new TaskDecompositionPromptBuilder();

        // 设置任务名称
        promptBuilder.setTaskName("帮我收集目标信息");

        // 添加高层次能力
        List<String> highLevelCapabilities = new ArrayList<>();
        highLevelCapabilities.add("目标靠近正上方");
        highLevelCapabilities.add("目标搜索");
        highLevelCapabilities.add("目标定点绕飞");
        highLevelCapabilities.add("返航");
        promptBuilder.addHighLevelCapabilities(highLevelCapabilities);

        // 添加低层次能力
        List<String> lowLevelActions = new ArrayList<>();
        lowLevelActions.add("无人机向（上/下/左/右/前/后）移动（1/2/3）米");
        lowLevelActions.add("无人机向（左/右）旋转（45/90/180）度");
        lowLevelActions.add("云台向下倾斜（30/60/90）度");
        lowLevelActions.add("以（5/6/7/8）米的半径以（lat、lon）为圆心定点绕飞");
        lowLevelActions.add("起飞");
        lowLevelActions.add("降落");
        lowLevelActions.add("指定航点（lon,lat）执行");
        lowLevelActions.add("切换当前任务目标为(子任务列表中的一个)");
        lowLevelActions.add("悬停");
        promptBuilder.addLowLevelActions(lowLevelActions);

        return promptBuilder.build();
    }

    /**
     * 解析任务分解结果
     */
    private JSONObject parseTaskDecomposition(String taskDecompositionResult) throws JSONException {
        JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(taskDecompositionResult);

        if (parseResult.getJsonData() == null) {
            logAndShowMessage("任务分解结果解析失败");
            throw new JSONException("任务分解结果解析失败");
        }

        return parseResult.getJsonData();
    }

    /**
     * 记录并显示任务分解信息
     */
    private void logAndDisplayTaskDecomposition() {
        try {
            String taskName = mTaskDecomposition.getString("任务名称");
            JSONArray taskFlow = mTaskDecomposition.getJSONArray("任务流程");

            StringBuilder summary = new StringBuilder();
            summary.append("任务名称: ").append(taskName).append("\n");
            summary.append("任务步骤数: ").append(taskFlow.length()).append("\n\n");

            for (int i = 0; i < taskFlow.length(); i++) {
                JSONObject step = taskFlow.getJSONObject(i);
                int stepNum = step.getInt("步骤");
                String subTask = step.getString("子任务");
                String description = step.getString("描述");

                summary.append("步骤 ").append(stepNum).append(": ")
                        .append(subTask).append("\n")
                        .append("描述: ").append(description).append("\n");

                JSONArray actions = step.getJSONArray("动作序列");
                summary.append("动作: [");
                for (int j = 0; j < actions.length(); j++) {
                    summary.append(actions.getString(j));
                    if (j < actions.length() - 1) summary.append(", ");
                }
                summary.append("]\n\n");
            }

            final String finalSummary = summary.toString();
            runOnUiThread(() -> {
                mCallback.addChatMessage(Constant.OWNER_BOT, "任务分解完成：\n" + finalSummary);
            });

        } catch (JSONException e) {
            logAndShowMessage("任务分解信息显示失败: " + e.getMessage());
        }
    }

    /**
     * 初始化VLM提示构建器
     */
    private void initializeVlmPromptBuilder() {
        // 初始化VLM提示构建器，第一个子任务通常是目标搜索
        try {
            JSONObject firstStep = mTaskDecomposition.getJSONArray("任务流程").getJSONObject(0);
            String firstSubTask = firstStep.getString("子任务");

            mPromptBuilder = new vlmPromptBuilder(mContext, MODEL_PATH, LABELS_PATH, firstSubTask);

            // 初始化子任务列表
            String[] subtasks = {"目标搜索", "靠近目标正上方", "目标定点绕飞", "返航"};
            mPromptBuilder.initializeSubtaskList(subtasks);

            // 初始化思维链
            mPromptBuilder.initializeAllThinkingChains();

        } catch (JSONException e) {
            logAndShowMessage("VLM提示构建器初始化失败: " + e.getMessage());
        }
    }

    /**
     * 执行任务流程
     */
    private void executeTaskFlow() {
        try {
            JSONArray taskFlow = mTaskDecomposition.getJSONArray("任务流程");
            int totalSteps = taskFlow.length();

            // 确保无人机已起飞
            ensureTakeoff();

            // 逐步执行任务
            for (mCurrentStep = 0; mCurrentStep < totalSteps; mCurrentStep++) {
                JSONObject stepInfo = taskFlow.getJSONObject(mCurrentStep);
                mCurrentHighLevelTask = stepInfo.getString("子任务");

                logAndShowMessage("开始执行步骤 " + (mCurrentStep + 1) + "/" + totalSteps
                        + ": " + mCurrentHighLevelTask);

                // 更新当前子任务
                mPromptBuilder.changeCurrentTaskObjectiveWithThinking(mCurrentHighLevelTask);

                // 执行高层次任务
                executeHighLevelTask(stepInfo);

                // 短暂休息，准备下一步
                sleepThread(SLEEP_BETWEEN_STEPS_MS);
            }

            logAndShowMessage("任务流程执行完成");
            mIsTaskRunning.set(false);

        } catch (Exception e) {
            logAndShowMessage("任务流程执行失败: " + e.getMessage());
            mIsTaskRunning.set(false);
        }
    }

    /**
     * 确保无人机已起飞
     */
    private void ensureTakeoff() {
        if (!mCallback.getisFlying()) {
            logAndShowMessage("无人机准备起飞...");
            mCommandInterpreter.mTakeoff();
            sleepThread(5000); // 等待起飞完成
        } else {
            logAndShowMessage("无人机已在空中，继续执行任务");
        }
    }

    /**
     * 执行高层次任务
     */
    private void executeHighLevelTask(JSONObject stepInfo) throws Exception {
        String subTask = stepInfo.getString("子任务");
        JSONArray actionSequence = stepInfo.getJSONArray("动作序列");

        // 根据不同的高层次任务执行不同的逻辑
        if (subTask.equals("目标搜索")) {
            executeSearchTask(actionSequence);
        } else if (subTask.equals("靠近目标正上方")) {
            executeApproachTask(actionSequence);
        } else if (subTask.equals("目标定点绕飞")) {
            executeCircleTask(actionSequence);
        } else if (subTask.equals("返航")) {
            executeReturnTask(actionSequence);
        } else {
            // 如果是未知的高层次任务，则直接执行动作序列
            executeActionSequence(actionSequence);
        }
    }

    /**
     * 执行目标搜索任务
     */
    private void executeSearchTask(JSONArray actionSequence) throws JSONException {
        logAndShowMessage("开始目标搜索任务");

        // 首先执行预定义的动作序列
        executeActionSequence(actionSequence);

        // 开始使用VLM进行搜索决策
        boolean targetFound = false;
        int searchAttempts = 0;
        final int MAX_SEARCH_ATTEMPTS = 10;

        while (!targetFound && searchAttempts < MAX_SEARCH_ATTEMPTS) {
            // 捕获当前画面
            File imageFile = captureImage();
            if (imageFile == null) {
                logAndShowMessage("无法捕获图像，跳过本次搜索");
                searchAttempts++;
                continue;
            }

            // 添加当前画面到提示
            Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            mPromptBuilder.addCurrentSceneDescription(bitmap);

            // 添加无人机位置
            addCurrentDronePosition();

            // 构建搜索决策提示并发送到VLM
            String vlmPrompt = mPromptBuilder.getFullPromptString();
            String vlmResponse = null;
            try {
                vlmResponse = mCallback.sendQuestionToGPTSync(vlmPrompt, imageFile, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // 解析VLM回复并执行动作
            JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(vlmResponse);
            if (parseResult.getJsonData() != null) {
                String action = parseResult.getJsonData().optString("action", "");
                String reasoning = parseResult.getInferenceProcess();

                logAndShowMessage("VLM推理过程: " + reasoning);

                if (action.contains("切换当前任务目标为靠近目标正上方")) {
                    // 目标找到，准备切换到靠近任务
                    logAndShowMessage("已找到目标，搜索任务完成");
                    targetFound = true;
                } else {
                    // 执行推荐的动作
                    executeVlmAction(action);
                    mPromptBuilder.addExecutedAction(action);
                }
            } else {
                logAndShowMessage("VLM回复解析失败，尝试执行预设搜索动作");
                mVirtualStickExecutor.mTurn(303, 30); // 旋转30度继续搜索
                sleepThread(2000);
            }

            searchAttempts++;
        }

        if (!targetFound) {
            logAndShowMessage("多次搜索未找到目标，强制进入下一任务阶段");
        }
    }

    /**
     * 执行靠近目标任务
     */
    private void executeApproachTask(JSONArray actionSequence) throws Exception {
        logAndShowMessage("开始靠近目标任务");

        // 首先执行预定义的动作序列
        executeActionSequence(actionSequence);

        // 使用VLM进行靠近决策
        boolean inPosition = false;
        int approachAttempts = 0;
        final int MAX_APPROACH_ATTEMPTS = 10;

        while (!inPosition && approachAttempts < MAX_APPROACH_ATTEMPTS) {
            // 捕获当前画面
            File imageFile = captureImage();
            if (imageFile == null) {
                logAndShowMessage("无法捕获图像，跳过本次调整");
                approachAttempts++;
                continue;
            }

            // 添加当前画面到提示
            Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            mPromptBuilder.addCurrentSceneDescription(bitmap);

            // 添加无人机位置
            addCurrentDronePosition();

            // 构建靠近决策提示并发送到VLM
            String vlmPrompt = mPromptBuilder.getFullPromptString();
            String vlmResponse = mCallback.sendQuestionToGPTSync(vlmPrompt, imageFile, true);

            // 解析VLM回复并执行动作
            JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(vlmResponse);
            if (parseResult.getJsonData() != null) {
                String action = parseResult.getJsonData().optString("action", "");
                String reasoning = parseResult.getInferenceProcess();

                logAndShowMessage("VLM推理过程: " + reasoning);

                if (action.contains("切换当前任务目标为目标定点绕飞") ||
                        reasoning.toLowerCase().contains("目标已居中") ||
                        reasoning.toLowerCase().contains("已到达目标上方")) {
                    // 已达到目标上方
                    logAndShowMessage("已到达目标上方，靠近任务完成");
                    inPosition = true;

                    // 悬停以稳定位置
                    mVirtualStickExecutor.mStop();
                    sleepThread(2000);
                } else {
                    // 执行推荐的动作
                    executeVlmAction(action);
                    mPromptBuilder.addExecutedAction(action);
                }
            } else {
                logAndShowMessage("VLM回复解析失败，尝试执行预设靠近动作");
                // 执行默认调整动作
                mVirtualStickExecutor.mGo(301, 1); // 向前移动1米
                sleepThread(2000);
            }

            approachAttempts++;
        }

        if (!inPosition) {
            logAndShowMessage("多次尝试未能精确靠近目标，强制进入下一任务阶段");
        }
    }

    /**
     * 执行目标环绕任务
     */
    private void executeCircleTask(JSONArray actionSequence) throws JSONException {
        logAndShowMessage("开始环绕目标任务");
        
        // 执行预定义的动作序列
        executeActionSequence(actionSequence);
        
        // 找出环绕半径
        double radius = 5.0; // 默认半径5米
        for (int i = 0; i < actionSequence.length(); i++) {
            String action = actionSequence.getString(i);
            if (action.contains("为圆心定点绕飞")) {
                radius = extractCircleRadius(action);
                break;
            }
        }
        
        // 记录当前位置作为环绕中心点
        if (mFlightController != null) {
            droneLocation = mFlightController.getState().getAircraftLocation();
            droneAlt = droneLocation.getAltitude();
            droneLat = droneLocation.getLatitude();
            droneLon = droneLocation.getLongitude();
            
            // 目标位置设置为当前位置（因为已经在目标正上方）
            objAlt = droneAlt;
            objLat = droneLat;
            objLon = droneLon;
            
            logAndShowMessage("环绕中心设置为: 经度=" + objLon + ", 纬度=" + objLat);
        }
        
        // 如果环绕半径小于2米，增加到2米以确保安全
        if (radius < 2.0) {
            radius = 2.0;
            logAndShowMessage("环绕半径过小，已调整为安全距离: " + radius + "米");
        }
        
        // 选择一种环绕方式执行
        // boolean useHotpointMission = true; // 选择热点绕飞还是航点绕飞
        boolean useHotpointMission = true; // 默认使用航点任务，更加精确
        
        if (useHotpointMission) {
            // 使用热点任务执行环绕
            logAndShowMessage("使用热点圆形绕飞任务执行环绕...");
            hotFlyCircle(radius);
        } else {
            // 使用航点任务执行环绕
            logAndShowMessage("使用航点任务执行环绕...");
            flyWithCircle(radius);
        }
        
        // 悬停并捕获更多目标信息
        sleepThread(15000); // 等待环绕任务完成
        logAndShowMessage("环绕完成，准备捕获目标信息");
        mVirtualStickExecutor.mStop();
        sleepThread(2000);
        
        // 捕获并分析目标图像
        File imageFile = captureImage();
        if (imageFile != null) {
            // 发送给VLM分析目标信息
            String infoPrompt = "请分析图像中的目标，提供详细描述，包括目标类型、外观特征、状态等信息。";
            try {
                String infoResponse = mCallback.sendQuestionToGPTSync(infoPrompt, imageFile, true);
                logAndShowMessage("目标信息分析结果: " + infoResponse);
            } catch (Exception e) {
                logAndShowMessage("目标信息分析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 执行返航任务
     */
    private void executeReturnTask(JSONArray actionSequence) throws JSONException {
        logAndShowMessage("开始返航任务");

        // 执行预定义的动作序列
        executeActionSequence(actionSequence);

        // 如果有初始位置信息，确保返回初始位置
        if (mInitialLocation != null) {
            // 构建返航航点指令
            String returnCommand = "指定航点(" +
                    mInitialLocation.getLongitude() + ", " +
                    mInitialLocation.getLatitude() + ")执行";

            logAndShowMessage("执行返航: " + returnCommand);
            executeVlmAction(returnCommand);
            sleepThread(5000); // 等待无人机到达返航点

            // 执行降落
            logAndShowMessage("到达返航点，准备降落");
            mCommandInterpreter.mLand();
        } else {
            // 如果没有初始位置，直接降落
            logAndShowMessage("无初始位置信息，直接降落");
            mCommandInterpreter.mLand();
        }
    }

    /**
     * 执行动作序列
     */
    private void executeActionSequence(JSONArray actionSequence) throws JSONException {
        for (int i = 0; i < actionSequence.length(); i++) {
            String action = actionSequence.getString(i);
            logAndShowMessage("执行动作: " + action);

            executeVlmAction(action);
            mPromptBuilder.addExecutedAction(action);

            // 在动作之间暂停一下，让无人机稳定
            sleepThread(1500);
        }
    }

    /**
     * 执行VLM生成的动作
     */
    private void executeVlmAction(String action) {
        if (action.contains("向上移动")) {
            int distance = extractDistance(action);
            mVirtualStickExecutor.mUp(distance);
        } else if (action.contains("向下移动")) {
            int distance = extractDistance(action);
            mVirtualStickExecutor.mDown(distance);
        } else if (action.contains("向左移动")) {
            int distance = extractDistance(action);
            mVirtualStickExecutor.mGo(303, distance);
        } else if (action.contains("向右移动")) {
            int distance = extractDistance(action);
            mVirtualStickExecutor.mGo(304, distance);
        } else if (action.contains("向前移动")) {
            int distance = extractDistance(action);
            mVirtualStickExecutor.mGo(301, distance);
        } else if (action.contains("向后移动")) {
            int distance = extractDistance(action);
            mVirtualStickExecutor.mGo(302, distance);
        } else if (action.contains("向左旋转")) {
            int angle = extractAngle(action);
            mVirtualStickExecutor.mTurn(303, angle);
        } else if (action.contains("向右旋转")) {
            int angle = extractAngle(action);
            mVirtualStickExecutor.mTurn(304, angle);
        } else if (action.contains("云台向下倾斜")) {
            int angle = extractAngle(action);
            mGimbalControl.pitchGimbalAbsolute(-angle); // 负值表示向下
        } else if (action.contains("定点绕飞")) {
            // 对于绕飞命令，需要提取半径和中心点
            try {
                int radius = extractCircleRadius(action);
                double[] centerPoint = extractCircleCenter(action);
                // 这里应该是实际的绕飞实现，但由于代码简化，我们只模拟一下
                logAndShowMessage("执行定点绕飞: 半径 " + radius + "米, 中心点: ["
                        + centerPoint[0] + ", " + centerPoint[1] + "]");
                // 模拟绕飞动作，实际应调用相应的API
                simulateCircleFlight(radius);
            } catch (Exception e) {
                logAndShowMessage("绕飞命令解析失败: " + e.getMessage());
            }
        } else if (action.contains("起飞")) {
            mCommandInterpreter.mTakeoff();
        } else if (action.contains("降落")) {
            mCommandInterpreter.mLand();
        } else if (action.contains("悬停")) {
            mVirtualStickExecutor.mStop();
        } else if (action.contains("指定航点")) {
            try {
                double[] waypoint = extractWaypoint(action);
                // 这里应该是实际的航点飞行实现
                logAndShowMessage("执行航点飞行: [" + waypoint[0] + ", " + waypoint[1] + "]");
                // 模拟航点飞行，实际应调用相应的API
                simulateWaypointFlight(waypoint);
            } catch (Exception e) {
                logAndShowMessage("航点命令解析失败: " + e.getMessage());
            }
        } else if (action.contains("切换当前任务目标为")) {
            // 从动作中提取任务目标
            String newTask = action.substring(action.indexOf("为") + 1).trim();
            logAndShowMessage("切换任务目标为: " + newTask);
            mPromptBuilder.changeCurrentTaskObjectiveWithThinking(newTask);
        }
    }

    /**
     * 从文本中提取距离数值
     */
    private int extractDistance(String text) {
        try {
            // 默认提取1米
            int defaultDistance = 1;

            // 尝试查找数字部分
            for (int i = 1; i <= 3; i++) {
                if (text.contains(i + "米")) {
                    return i;
                }
            }

            return defaultDistance;
        } catch (Exception e) {
            return 1; // 默认返回1米
        }
    }

    /**
     * 从文本中提取角度数值
     */
    private int extractAngle(String text) {
        try {
            if (text.contains("45度")) return 45;
            if (text.contains("90度")) return 90;
            if (text.contains("180度")) return 180;
            if (text.contains("30度")) return 30;
            if (text.contains("60度")) return 60;

            return 30; // 默认返回30度
        } catch (Exception e) {
            return 30; // 默认返回30度
        }
    }

    /**
     * 从绕飞命令中提取半径
     */
    private int extractCircleRadius(String text) {
        try {
            for (int i = 5; i <= 8; i++) {
                if (text.contains(i + "米的半径")) {
                    return i;
                }
            }
            return 5; // 默认返回5米半径
        } catch (Exception e) {
            return 5; // 默认返回5米半径
        }
    }

    /**
     * 从绕飞命令中提取中心点坐标
     */
    private double[] extractCircleCenter(String text) {
        try {
            // 查找坐标信息
            int startIndex = text.indexOf("(");
            int endIndex = text.indexOf(")");

            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                String coordsStr = text.substring(startIndex + 1, endIndex);
                String[] coords = coordsStr.split(",");

                if (coords.length == 2) {
                    double lon = Double.parseDouble(coords[0].trim());
                    double lat = Double.parseDouble(coords[1].trim());
                    return new double[]{lon, lat};
                }
            }

            // 如果没有找到或解析失败，使用当前无人机位置
            if (mFlightController != null) {
                LocationCoordinate3D location = mFlightController.getState().getAircraftLocation();
                return new double[]{location.getLongitude(), location.getLatitude()};
            }

            // 默认返回空坐标
            return new double[]{0, 0};
        } catch (Exception e) {
            // 出错时使用当前无人机位置
            if (mFlightController != null) {
                LocationCoordinate3D location = mFlightController.getState().getAircraftLocation();
                return new double[]{location.getLongitude(), location.getLatitude()};
            }
            return new double[]{0, 0};
        }
    }

    /**
     * 从航点命令中提取航点坐标
     */
    private double[] extractWaypoint(String text) {
        try {
            // 查找坐标信息
            int startIndex = text.indexOf("(");
            int endIndex = text.indexOf(")");

            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                String coordsStr = text.substring(startIndex + 1, endIndex);
                String[] coords = coordsStr.split(",");

                if (coords.length == 2) {
                    double lon = Double.parseDouble(coords[0].trim());
                    double lat = Double.parseDouble(coords[1].trim());
                    return new double[]{lon, lat};
                }
            }

            // 如果没有找到或解析失败，使用初始无人机位置
            if (mInitialLocation != null) {
                return new double[]{mInitialLocation.getLongitude(), mInitialLocation.getLatitude()};
            }

            // 默认返回空坐标
            return new double[]{0, 0};
        } catch (Exception e) {
            // 出错时使用初始无人机位置
            if (mInitialLocation != null) {
                return new double[]{mInitialLocation.getLongitude(), mInitialLocation.getLatitude()};
            }
            return new double[]{0, 0};
        }
    }

    //TODO 更改动作
    /**
     * 模拟绕飞动作
     * 注：实际应用中应替换为DJI SDK的实际绕飞实现
     */
    private void simulateCircleFlight(int radius) {
        logAndShowMessage("模拟执行绕飞，半径: " + radius + "米");

        // 在实际应用中，应该使用DJI的Mission API创建绕飞任务
        // 这里为了简化，我们只是模拟一个绕飞的效果

        // 向右旋转一周，分四段执行
        mVirtualStickExecutor.mTurn(304, 90);
        sleepThread(2000);
        mVirtualStickExecutor.mTurn(304, 90);
        sleepThread(2000);
        mVirtualStickExecutor.mTurn(304, 90);
        sleepThread(2000);
        mVirtualStickExecutor.mTurn(304, 90);
        sleepThread(2000);

        // 绕飞结束，悬停
        mVirtualStickExecutor.mStop();
    }

    /**
     * 获取热点任务操作器
     */
    private HotpointMissionOperator getHotpointMissionOperator() {
        return DJISDKManager.getInstance().getMissionControl().getHotpointMissionOperator();
    }
    
    /**
     * 获取航点任务操作器
     */
    private WaypointMissionOperator getWaypointMissionOperator() {
        return DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
    }

    /**
     * 执行航点任务飞行
     * 使用DJI的WaypointMission API创建一个简单的航点任务
     * @param waypoint 目标航点
     */
    private void simulateWaypointFlight(double[] waypoint) {
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final WaypointMission.Builder waypointMissionBuilder = new WaypointMission.Builder();
            
            // 参数配置
            waypointMissionBuilder.autoFlightSpeed(3.0f)  // 设置自动飞行速度
                    .maxFlightSpeed(5.0f)                // 最大飞行速度
                    .setExitMissionOnRCSignalLostEnabled(false)
                    .finishedAction(WaypointMissionFinishedAction.GO_HOME)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                    .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY)
                    .headingMode(WaypointMissionHeadingMode.AUTO);
            
            // 获取当前位置作为起点
            LocationCoordinate3D currentLocation = null;
            if (mFlightController != null) {
                currentLocation = mFlightController.getState().getAircraftLocation();
                logAndShowMessage("当前位置: 经度=" + currentLocation.getLongitude() + ", 纬度=" + currentLocation.getLatitude() + ", 高度=" + currentLocation.getAltitude());
            } else {
                logAndShowMessage("无法获取当前位置，航点任务无法执行");
                return;
            }
            
            // 添加当前位置作为第一个航点
            Waypoint startWaypoint = new Waypoint(currentLocation.getLatitude(), currentLocation.getLongitude(), currentLocation.getAltitude());
            startWaypoint.addAction(new WaypointAction(WaypointActionType.STAY, 1000)); // 停留1秒
            waypointMissionBuilder.addWaypoint(startWaypoint);
            
            // 添加目标位置作为第二个航点
            Waypoint targetWaypoint = new Waypoint(waypoint[0], waypoint[1], (float) waypoint[2]);
            targetWaypoint.addAction(new WaypointAction(WaypointActionType.STAY, 5000)); // 到达目标点后停留5秒
            targetWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0)); // 拍照
            waypointMissionBuilder.addWaypoint(targetWaypoint);
            
            // 加载任务
            DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
            if (error == null) {
                logAndShowMessage("航点任务加载成功");
                
                // 添加航点任务监听器
                getWaypointMissionOperator().addListener(new WaypointMissionOperatorListener() {
                    @Override
                    public void onExecutionStart() {
                        logAndShowMessage("航点任务开始执行");
                    }
                    
                    @Override
                    public void onExecutionFinish(@Nullable final DJIError error) {
                        if (error == null) {
                            logAndShowMessage("航点任务执行完成");
                        } else {
                            logAndShowMessage("航点任务执行失败: " + error.getDescription());
                        }
                        latch.countDown(); // 解除阻塞
                    }
                    
                    @Override
                    public void onExecutionUpdate(@NonNull WaypointMissionExecutionEvent event) {
                        logAndShowMessage("航点执行进度: " + event.getProgress().targetWaypointIndex + "/2");
                    }
                    
                    @Override
                    public void onDownloadUpdate(@NonNull WaypointMissionDownloadEvent event) {}
                    
                    @Override
                    public void onUploadUpdate(@NonNull WaypointMissionUploadEvent event) {}
                });
                
                // 开始上传任务
                getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            logAndShowMessage("航点任务上传失败: " + djiError.getDescription());
                            latch.countDown(); // 解除阻塞
                        }
                    }
                });
                
                // 等待任务完成
                try {
                    latch.await(180, TimeUnit.SECONDS); // 等待最多3分钟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logAndShowMessage("航点任务等待被中断");
                }
                
            } else {
                logAndShowMessage("航点任务加载失败: " + error.getDescription());
            }
            
        } catch (Exception e) {
            logAndShowMessage("执行航点任务异常: " + e.getMessage());
        }
    }

    /**
     * 添加当前无人机位置到提示构建器
     */
    private void addCurrentDronePosition() {
        if (mFlightController != null) {
            LocationCoordinate3D location = mFlightController.getState().getAircraftLocation();
            mPromptBuilder.addDronePosition(location.getLongitude(), location.getLatitude());

            // 更新无人机状态
            double[] speed = {
                    mFlightController.getState().getVelocityX(),
                    mFlightController.getState().getVelocityY(),
                    mFlightController.getState().getVelocityZ()
            };

            double[] attitude = {
                    mFlightController.getState().getAttitude().yaw,
                    mFlightController.getState().getAttitude().roll,
                    mFlightController.getState().getAttitude().pitch
            };

            mPromptBuilder.updateDroneState(
                    speed,
                    attitude,
                    mFlightController.getState().isFlying(),
                    mFlightController.getState().getSatelliteCount(), // 使用卫星数作为GPS信号等级
                    0 // 没有风力数据，默认为0
            );
        }
    }

    /**
     * 捕获当前图像
     */
    private File captureImage() {
        Bitmap bitmap = mTextureView.getBitmap();
        if (bitmap == null) {
            logAndShowMessage("未能捕获视频帧，TextureView 可能未准备好");
            return null;
        }

        File imageFile = saveBitmapAsFile(bitmap, IMAGE_FILE_NAME);
        if (imageFile == null) {
            logAndShowMessage("图片保存失败");
            return null;
        }

        return imageFile;
    }

    /**
     * 保存位图为文件
     */
    private File saveBitmapAsFile(Bitmap bitmap, String filename) {
        File file = new File(mContext.getCacheDir(), filename); // 保存到应用的缓存目录
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // 压缩并保存为JPEG
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return file;
    }

    /**
     * 线程休眠
     */
    private void sleepThread(int timeMs) {
        try {
            Thread.sleep(timeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logAndShowMessage("线程被中断");
        }
    }

    /**
     * 记录日志并显示消息
     */
    private void logAndShowMessage(String message) {
        Log.d(TAG, message);
        runOnUiThread(() -> {
            mCallback.addChatMessage(Constant.OWNER_BOT, message);
        });
    }

    /**
     * 中止当前任务
     */
    public void abortMission() {
        if (mIsTaskRunning.get()) {
            logAndShowMessage("正在中止任务...");
            mIsTaskRunning.set(false);

            // 停止无人机，确保安全
            mVirtualStickExecutor.mStop();

            // 清理资源
            if (mPromptBuilder != null) {
                mPromptBuilder.close();
            }

            logAndShowMessage("任务已中止");
        } else {
            logAndShowMessage("没有运行中的任务");
        }
    }

    /**
     * 释放资源
     */
    public void dispose() {
        abortMission();
        mExecutorService.shutdown();
    }

    /**
     * 获取当前任务执行状态
     */
    public boolean isTaskRunning() {
        return mIsTaskRunning.get();
    }

    /**
     * 使用航点任务实现圆形飞行
     * @param radius 圆形半径（米）
     */
    private void flyWithCircle(double radius) {
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final WaypointMission.Builder waypointMissionBuilder = new WaypointMission.Builder();
            
            // 参数配置
            waypointMissionBuilder.autoFlightSpeed(2.0f)  // 设置自动飞行速度
                    .maxFlightSpeed(3.0f)                // 最大飞行速度
                    .setExitMissionOnRCSignalLostEnabled(false)
                    .finishedAction(WaypointMissionFinishedAction.GO_HOME)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                    .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY)
                    .headingMode(WaypointMissionHeadingMode.AUTO);
                    
            // 生成圆形路径的航点（16个点）
            final int waypointCount = 16;
            final double angleIncrement = 2 * Math.PI / waypointCount;
            
            // 生成航点集合
            for (int i = 0; i < waypointCount; i++) {
                double angle = i * angleIncrement;
                double offsetX = radius * Math.cos(angle);
                double offsetY = radius * Math.sin(angle);
                
                // 计算GPS坐标
                double latitude = droneLat + (offsetY / 111111.0); // 纬度偏移，近似为1度=111111米
                double longitude = droneLon + (offsetX / (111111.0 * Math.cos(Math.toRadians(droneLat)))); // 经度偏移
                
                Waypoint waypoint = new Waypoint(latitude, longitude, (float) droneAlt);
                waypoint.shootPhotoTimeInterval = 2;  // 每2秒拍摄一张照片
                
                // 配置航点动作（可根据需要添加悬停、拍照等动作）
                // 第一个点和最后一个点停留时间长一些
                if (i == 0 || i == waypointCount - 1) {
                    waypoint.addAction(new WaypointAction(WaypointActionType.STAY, 5000)); // 停留5秒
                }
                
                waypointMissionBuilder.addWaypoint(waypoint);
            }
            
            DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
            if (error == null) {
                logAndShowMessage("航点任务加载成功");
                
                // 添加航点任务监听器
                getWaypointMissionOperator().addListener(new WaypointMissionOperatorListener() {
                    @Override
                    public void onExecutionStart() {
                        logAndShowMessage("航点任务开始执行");
                    }
                    
                    @Override
                    public void onExecutionFinish(@Nullable final DJIError error) {
                        if (error == null) {
                            logAndShowMessage("航点任务执行完成");
                        } else {
                            logAndShowMessage("航点任务执行失败: " + error.getDescription());
                        }
                        latch.countDown(); // 解除阻塞
                    }
                    
                    @Override
                    public void onExecutionUpdate(@NonNull WaypointMissionExecutionEvent event) {
                        logAndShowMessage("航点执行进度: " + event.getProgress().targetWaypointIndex + "/" + waypointCount);
                    }
                    
                    @Override
                    public void onDownloadUpdate(@NonNull WaypointMissionDownloadEvent event) {}
                    
                    @Override
                    public void onUploadUpdate(@NonNull WaypointMissionUploadEvent event) {}

                });
                
                // 开始上传任务
                getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            logAndShowMessage("航点任务上传失败: " + djiError.getDescription());
                            latch.countDown(); // 解除阻塞
                        }
                    }
                });
                
                // 等待任务完成
                try {
                    latch.await(300, TimeUnit.SECONDS); // 等待最多5分钟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logAndShowMessage("航点任务等待被中断");
                }
                
            } else {
                logAndShowMessage("航点任务加载失败: " + error.getDescription());
            }
        } catch (Exception e) {
            logAndShowMessage("执行航点任务异常: " + e.getMessage());
        }
    }
    
    /**
     * 使用热点任务实现圆形飞行
     * @param radius 圆形半径（米）
     */
    private void hotFlyCircle(double radius) {
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            
            // 创建热点任务
            final HotpointMission hotpointMission = new HotpointMission();
            
            // 设置热点任务参数
            hotpointMission.setHotpoint(new LocationCoordinate2D(objLat, objLon));
            hotpointMission.setRadius((float) radius);
            hotpointMission.setAngularVelocity(10);  // 角速度，单位为度/秒
            hotpointMission.setStartPoint(HotpointStartPoint.NEAREST);
            hotpointMission.setClockwise(true);      // 顺时针飞行
            hotpointMission.setHeading(HotpointHeading.TOWARDS_HOT_POINT); // 相机始终朝向热点
            
            // 获取热点任务操作器
            final HotpointMissionOperator hotpointMissionOperator = getHotpointMissionOperator();
            
            // 添加监听器
            hotpointMissionOperator.addListener(new HotpointMissionOperatorListener() {
                @Override
                public void onExecutionUpdate(@NonNull HotpointMissionEvent hotpointMissionEvent) {

                }

                @Override
                public void onExecutionStart() {
                    logAndShowMessage("热点任务开始执行");
                }

                
                @Override
                public void onExecutionFinish(@Nullable final DJIError error) {
                    if (error == null) {
                        logAndShowMessage("热点任务执行完成");
                    } else {
                        logAndShowMessage("热点任务执行失败: " + error.getDescription());
                    }
                    latch.countDown(); // 解除阻塞
                }

            });
            
            // 启动热点任务
            hotpointMissionOperator.startMission(hotpointMission, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        logAndShowMessage("热点任务启动失败: " + djiError.getDescription());
                        latch.countDown(); // 解除阻塞
                    } else {
                        logAndShowMessage("热点任务启动成功");
                        
                        // 设置一个计时器，让热点任务执行一定时间后停止
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                logAndShowMessage("热点任务执行时间到，准备停止...");
                                hotpointMissionOperator.stop(new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError != null) {
                                            logAndShowMessage("热点任务停止失败: " + djiError.getDescription());
                                        } else {
                                            logAndShowMessage("热点任务已停止");
                                        }
                                        latch.countDown(); // 解除阻塞
                                    }
                                });
                            }
                        }, 60000); // 执行60秒后停止
                    }
                }
            });
            
            // 等待任务完成
            try {
                latch.await(300, TimeUnit.SECONDS); // 等待最多5分钟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logAndShowMessage("热点任务等待被中断");
            }
            
        } catch (Exception e) {
            logAndShowMessage("执行热点任务异常: " + e.getMessage());
        }
    }

    /**
     * 执行指定航点任务
     */
    private void executeWaypointTask(JSONArray actionSequence) throws JSONException {
        logAndShowMessage("开始执行航点任务");
        
        // 执行预定义的动作序列
        executeActionSequence(actionSequence);
        
        // 查找航点信息
        double[] waypoint = null;
        for (int i = 0; i < actionSequence.length(); i++) {
            String action = actionSequence.getString(i);
            if (action.contains("飞到") || action.contains("飞向") || action.contains("飞至")) {
                waypoint = extractWaypoint(action);
                break;
            }
        }
        
        if (waypoint != null) {
            logAndShowMessage("目标航点: 纬度=" + waypoint[0] + ", 经度=" + waypoint[1] + ", 高度=" + waypoint[2]);
            
            // 执行航点飞行
            simulateWaypointFlight(waypoint);
            
            // 悬停并捕获目标信息
            sleepThread(2000);
            logAndShowMessage("到达目标航点，准备捕获目标信息");
            mVirtualStickExecutor.mStop();
            sleepThread(2000);
            
            // 捕获并分析目标图像
            File imageFile = captureImage();
            if (imageFile != null) {
                // 发送给VLM分析目标信息
                String infoPrompt = "请分析图像中的目标，提供详细描述，包括目标类型、外观特征、状态等信息。";
                try {
                    String infoResponse = mCallback.sendQuestionToGPTSync(infoPrompt, imageFile, true);
                    logAndShowMessage("目标信息分析结果: " + infoResponse);
                } catch (Exception e) {
                    logAndShowMessage("目标信息分析失败: " + e.getMessage());
                }
            }
        } else {
            logAndShowMessage("未找到有效的航点信息，任务取消");
        }
    }
}