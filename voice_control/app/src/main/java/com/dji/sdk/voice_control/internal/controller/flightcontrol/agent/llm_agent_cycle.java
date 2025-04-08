package com.dji.sdk.voice_control.internal.controller.flightcontrol.agent;

import static com.dji.sdk.voice_control.internal.djidemo.utils.ToastUtils.setResultToToast;
import static com.dji.sdk.voice_control.internal.djidemo.utils.ToastUtils.showToast;
import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import dji.common.util.CommonCallbacks;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.controller.MainActivity;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.djitool.gimbal.gimbalControl;
import com.dji.sdk.voice_control.internal.controller.djitool.waypoint.Waypointv1;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.interfaces.ControlActivityCallback;
import com.dji.sdk.voice_control.internal.controller.utils.JsonUtils;
import com.dji.sdk.voice_control.internal.controller.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dji.common.error.DJIError;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.mission.hotpoint.HotpointHeading;
import dji.common.mission.hotpoint.HotpointMission;
import dji.common.mission.hotpoint.HotpointMissionEvent;
import dji.common.mission.hotpoint.HotpointStartPoint;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.model.LocationCoordinate2D;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.hotpoint.HotpointMissionOperator;
import dji.sdk.mission.hotpoint.HotpointMissionOperatorListener;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.sdkmanager.DJISDKManager;

public class llm_agent_cycle {

    private FlightController mFlightController;
    private CommandInterpreter mCI;
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    private TextureView mfpvTexture;
    private ControlActivityCallback callback;
    private HotpointMissionOperator hotpointMissionOperator = null;     // 圆形绕飞任务控制器
    private HotpointMissionOperatorListener hotpointlistener;

    private static final String TAG = "llm_agent_cycle";

    //region agent 数据结构
    private static final String AGENT_URL = "http://122.207.106.69:25130/chat";
    private static final String TEMPLATE="Please answer the following question: {question}";
    // 常量定义
    private static final int MAX_SEARCH_ATTEMPTS = 100;
    private static final int SLEEP_BETWEEN_SEARCH_MS = 5000;
    private static final int CLOSE_POSITION_PROPORTION_THRESHOLD = 60;
    private static final int COMMAND_UP_ANGLE = 5;
    private static final String IMAGE_FILE_NAME = "frame.jpg";
    // 共享变量
    private volatile boolean isfindCar = false; // 是否满足在中心且占比>=70
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    // 移动距离配置
    private static final double MIN_MOVE_DISTANCE = 0.5; // 最小移动距离（米）
    private static final double MAX_MOVE_DISTANCE = 3.0; // 最大移动距离（米）
    private static final int PROPORTION_THRESHOLD = 60; // 占比阈值

    // 添加新的实例变量
    private String targetObjectType = "黑色轿车"; // 默认值是黑色轿车

    //提示词模板
    private static final String DIRECTION_PROMPT_TEMPLATE = "请分析图像，回答以下问题。首先，详细描述您的推理过程。然后，将您的答案以JSON格式输出。\n" +
            "\n" +
            "推理过程：\n" +
            "- 描述您如何判断图中是否有%s,置信度水平如何。\n" +
            "- 解释您对%s位置（左、中、右）的判断依据。\n" +
            "- 描述您如何估算%s占据图像的比例。\n" +
            "\n" +
            "请在推理过程之后，输出JSON格式的答案：\n" +
            "\n" +
            "{\n" +
            "  \"has_object\": 布尔值（true或false），表示您认为是否有%s, \n" +
            "  \"confidence_percentage\": 整数，范围0-100，表示您认为图中有%s的把握，\n" +
            "  \"location_description\": \"字符串，'left'、'center' 或 'right'，描述%s在图像中的位置\",\n" +
            "  \"estimated_proportion_percentage\": 整数，范围0-100，估计%s占据图像的比例，\n" +
            "}\n" +
            "\n" +
            "**注意：**\n" +
            "- 请先输出推理过程，然后在下一行输出JSON对象。\n" +
            "- 不要在JSON对象之外添加额外的文本或注释。\n" +
            "- 请避免使用诸如抱歉，我无法查看或分析图片内容的句子，尽可能基于图像提供回答。\n" +
            "- 只需要判断目标在图像的左、右或者中间，不要回复类似左中(center-left)的回答。\n" +
            "- 请注意%s通常具有完整%s轮廓。";

    //提示词模板
    private static final String CENTER_PROMPT_TEMPLATE = "请分析图像，回答以下问题。首先，详细描述您的推理过程。然后，将您的答案以JSON格式输出。\n" +
            "\n" +
            "推理过程：\n" +
            "- 描述您如何判断图中是否有%s,置信度水平如何。\n" +
            "- 解释您对%s位置（中、上，下）的判断依据。\n" +
            "- 描述您如何估算%s占据图像的比例。\n" +
            "\n" +
            "请在推理过程之后，输出JSON格式的答案：\n" +
            "\n" +
            "{\n" +
            "  \"has_object\": 布尔值（true或false），表示您认为是否有%s,\n" +
            "  \"confidence_percentage\": 整数，范围0-100，表示您认为图中有%s的把握，\n" +
            "  \"location_description\": \"字符串，'forward'、'center' 或 'backward'，描述%s在图像中的位置\",\n" +
            "  \"estimated_proportion_percentage\": 整数，范围0-100，估计%s占据图像的比例，\n" +
            "}\n" +
            "\n" +
            "**注意：**\n" +
            "- 请先输出推理过程，然后在下一行输出JSON对象。\n" +
            "- 不要在JSON对象之外添加额外的文本或注释。\n" +
            "- 请避免使用诸如抱歉，我无法查看或分析图片内容的句子，尽可能基于图像提供回答。\n" +
            "- 只需要判断目标在图像的前、后或者中间，不要回复类似左中(center-left)的回答。\n" +
            "- 请注意%s通常具有完整%s轮廓。";

    // 格式化后的提示词
    private String direction_prompt;
    private String center_prompt;
    private String Gpt_result;

    //无人机信息
    LocationCoordinate3D droneLocation = new LocationCoordinate3D(0,0,0);
    double droneLon = 0.0;
    double droneLat = 0.0;
    double droneAlt = 0.0;

    double objLon = 0.0;
    double objLat = 0.0;
    double objAlt = 0.0;

    //航点数据结构
    private Waypointv1 mWaypoint;
    private WaypointMissionOperator mMissionOperator;
    private gimbalControl gimbalControl;

    private boolean isend = false;
    private float reduis = 0f;
    //endregion

    //构造函数
    public llm_agent_cycle(
            CommandInterpreter commandInterpreter,
            FlightController flightController,
            TextureView textureView,
            ControlActivityCallback callback
    ){
        this.mCI = commandInterpreter;
        this.mFlightController = flightController;
        this.mfpvTexture = textureView;
        this.callback = callback;
        
        // 使用默认值格式化提示词
        this.formatPrompts();
    }

    public void setTargetObjectType(String targetObjectType) {
        this.targetObjectType = targetObjectType;
        this.formatPrompts();
    }

    /**
     * 格式化提示词，将模板中的占位符替换为目标物体类型
     */
    private void formatPrompts() {
        // 格式化方向提示词
        this.direction_prompt = String.format(DIRECTION_PROMPT_TEMPLATE, 
            this.targetObjectType, this.targetObjectType, this.targetObjectType,
            this.targetObjectType, this.targetObjectType, this.targetObjectType,
            this.targetObjectType, this.targetObjectType, this.targetObjectType);
        
        // 格式化居中提示词
        this.center_prompt = String.format(CENTER_PROMPT_TEMPLATE,
            this.targetObjectType, this.targetObjectType, this.targetObjectType,
            this.targetObjectType, this.targetObjectType, this.targetObjectType,
            this.targetObjectType, this.targetObjectType, this.targetObjectType);
        
        Log.d(TAG, "formatPrompts: 提示词已格式化为目标类型: " + this.targetObjectType);
    }

    //region Agent控制

    /**
     * 入口函数 - 寻找目标物体
     */
    public void agentFindTarget() {
        // 开启新线程 锁定目标线程
        new Thread(() -> {
            // 初始化虚拟摇杆执行器
            mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();

            //起飞
            if(!callback.getisFlying()){
                mCI.mTakeoff();
            }
            SleepThread(SLEEP_BETWEEN_SEARCH_MS);

            //设置一个合理的飞行高度
            //向上飞8米
            if(callback.gerAltitude()<18){
                mSingletonVirtualStickExecutor.mUp((int) (18f - callback.gerAltitude()));
            }
            if(callback.gerAltitude()>18){
                mSingletonVirtualStickExecutor.mUp((int) (callback.gerAltitude() - 18f));
            }
            SleepThread(SLEEP_BETWEEN_SEARCH_MS);

            try {
                //开始锁定目标
                performSearch(1, MAX_SEARCH_ATTEMPTS);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    //搜索

    /**
     * 在场景中自动搜索目标物体
     * @return
     */
    private boolean doSearch(int attemptIndex) throws Exception {
        // 如果超出最大次数，就结束
        if (attemptIndex >= MAX_SEARCH_ATTEMPTS) {
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, String.format("多次搜索仍未找到%s。", targetObjectType));
            });
            return false;
        }
        final boolean[] result = { false }; // 存放是否找到目标
        File imageFile = CaptureImage();
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        runOnUiThread(() -> {callback.addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");});
        runOnUiThread(() -> {callback.addChatMessage(Constant.OWNER_HUMAN, bitmap);});
        runOnUiThread(() -> {callback.addChatMessage(Constant.OWNER_BOT, "思考中...");});


        runOnUiThread(() -> {showToast("成功");});
        String gptResult = callback.sendQuestionToGPTSync(direction_prompt, imageFile,true);
        JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(gptResult);
        String response = parseResult.getInferenceProcess();

        if (parseResult.getJsonData() == null) {
            runOnUiThread(() -> {callback.addChatMessage(Constant.OWNER_BOT, "模型返回为空，尝试下一帧...");});
        } else {
            boolean hasTarget = parseResult.getJsonData().optBoolean("has_object", false);
            int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);

            if (hasTarget && confidence >= 80) {
                result[0] = true;
                response += String.format("\n%s已锁定!", targetObjectType);
                String finalResponse = response;
                runOnUiThread(() -> {callback.addChatMessage(Constant.OWNER_BOT, finalResponse);});
                //目标靠近线程
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        count_false_front = 0;
                        count_false_back = 0;
                        count_center = 0;
                        current_gimbal_angle = 0;
                        Close_to();
                    }
                }).start();
            } else {
                response += String.format("\n未能识别到目标%s，继续搜索...", targetObjectType);
                String finalResponse1 = response;
                runOnUiThread(() -> {callback.addChatMessage(Constant.OWNER_BOT, finalResponse1);});
                // 转动视角
                MyVirtualStickExecutor executor = MyVirtualStickExecutor.getUniqueInstance();
                executor.mTurn(303, 10);
                Log.d(TAG, String.format("未能识别%s，旋转10度", targetObjectType));
                SleepThread(3000);
            }
        }
        if(!result[0]){
            doSearch(attemptIndex + 1);
        }
        return result[0];
    }

    /**
     * 执行搜索并根据结果决定是否上升和继续搜索
     *
     * @param currentAttempt 当前尝试次数
     * @param maxAttempts    最大尝试次数
     */
    private void performSearch(int currentAttempt, int maxAttempts) throws Exception {
        boolean isFind = doSearch(0);
        if (isFind) {
            return;
        }

        if (currentAttempt < maxAttempts) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "第 " + currentAttempt + " 次搜索未找到，开始上升 "));
            mSingletonVirtualStickExecutor.mUp(3);
            // 睡 6 秒再搜下一次
            SleepThread(SLEEP_BETWEEN_SEARCH_MS);
            performSearch(currentAttempt+1,maxAttempts);
        } else {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, String.format("多次搜索仍未找到%s。请检查坐标或场景是否正确。", targetObjectType)));
            mSingletonVirtualStickExecutor.mStop();
        }
    }

    //靠近

    /**
     * 根据识别到的目标物体信息，进行"靠近"操作。
     */
    public void Close_to() {
        performCloseToSearch(1, MAX_SEARCH_ATTEMPTS);
    }

    boolean use_front = true;
    int count_false_back = 0;
    int count_false_front = 0;
    int count_center = 0;
    int current_gimbal_angle = 0;
    /**
     * 递归执行靠近搜索，直到满足条件或达到最大尝试次数
     */
    @SuppressLint("DefaultLocale")
    private void performCloseToSearch(int currentAttempt, int maxAttempts) {
        if( currentAttempt>maxAttempts ){
            recognizeCarBrand();
            return;
        }
        if (isfindCar) {
            return;
        }

        if(use_front){
            File imageFile = CaptureImage();
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

            callback.addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");
            callback.addChatMessage(Constant.OWNER_HUMAN, bitmap);
            callback.addChatMessage(Constant.OWNER_BOT, "思考中...");

            try {
                String gptResult = callback.sendQuestionToGPTSync(direction_prompt, imageFile, true);
                JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(gptResult);
                String response = parseResult.getInferenceProcess();

                if (parseResult.getJsonData() == null) {
                    callback.addChatMessage(Constant.OWNER_BOT, "模型返回为空，尝试下一帧...");
                } else {
                    String locationDesc = parseResult.getJsonData().optString("location_description", "center");
                    int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);
                    int proportion = parseResult.getJsonData().optInt("estimated_proportion_percentage", 0);
                    boolean hasTarget = parseResult.getJsonData().optBoolean("has_object", false);

                    if(hasTarget && confidence>=80){
                        callback.addChatMessage(Constant.OWNER_BOT,
                                String.format("开始靠近%s —— 位置: %s, 置信度: %d%%, 占比: %d%%",
                                        targetObjectType, locationDesc, confidence, proportion)
                        );
                        // 调整无人机位置
                        adjustDronePosition(locationDesc, proportion);
                    } else {
                        count_false_front ++;
                        if(count_false_front == 2){
                            count_false_front = 0;
                            current_gimbal_angle -= 30;
                            gimbalControl gimbalControl = new gimbalControl();
                            gimbalControl.pitchGimbalAbsolute(current_gimbal_angle);
                            if(current_gimbal_angle == -90){
                                use_front = false;
                            }
                            callback.addChatMessage(Constant.OWNER_BOT,"正在切换俯视图");
                        }
                    }
                }
            } catch (Exception e) {
                callback.addChatMessage(Constant.OWNER_BOT, "解析结果时出错: " + e.getMessage());
                Log.d(TAG,"前视出错: " + e.getMessage());
            }
        } else{
            File imageFile = CaptureImage();
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

            callback.addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");
            callback.addChatMessage(Constant.OWNER_HUMAN, bitmap);
            callback.addChatMessage(Constant.OWNER_BOT, "思考中...");

            try {
                String gptResult = callback.sendQuestionToGPTSync(center_prompt, imageFile, true);
                JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(gptResult);
                String response = parseResult.getInferenceProcess();

                if (parseResult.getJsonData() == null) {
                    callback.addChatMessage(Constant.OWNER_BOT, "模型返回为空，尝试下一帧...");
                } else {
                    String locationDesc = parseResult.getJsonData().optString("location_description", "center");
                    int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);
                    int proportion = parseResult.getJsonData().optInt("estimated_proportion_percentage", 0);
                    boolean hasTarget = parseResult.getJsonData().optBoolean("has_car", false);

                    if(hasTarget && confidence>=80){
                        callback.addChatMessage(Constant.OWNER_BOT,
                                String.format("开始靠近%s —— 位置: %s, 置信度: %d%%, 占比: %d%%",
                                        targetObjectType, locationDesc, confidence, proportion)
                        );
                        if(!locationDesc.equals("center")){
                            // 调整无人机位置
                            adjustDronePosition(locationDesc, proportion);
                        } else{
                            count_center ++;
                            if(count_center == 2){
                                Log.d(TAG, String.format("已经位于%s中央", targetObjectType));
                                isfindCar = true;
                                hotFlyCircle(5);
                            }
                        }
                    } else {
                        count_false_back ++;
                        if(count_false_back == 3){
                            performSearch(0,MAX_SEARCH_ATTEMPTS);
                            callback.addChatMessage(Constant.OWNER_BOT,"俯视图和前视图均未找到，重新搜索场景");
                        }
                    }
                }
            } catch (Exception e) {
                callback.addChatMessage(Constant.OWNER_BOT, "解析结果时出错: " + e.getMessage());
                Log.e(TAG,"俯视搜索出错: " + e.getMessage());
            }
        }

        if (!isfindCar) {
            // 递归调用，继续靠近搜索
            performCloseToSearch(currentAttempt+1,maxAttempts);
        }
    }

    /**
     * 调整无人机的位置或视角，使目标物体更居中。
     * 当目标物体已在中心 (center) 时，若占比 < 阈值，则向前移动一定距离。
     * @param locationDesc 目标物体在画面中的位置描述 (left/right/center)
     * @param proportion   目标物体在画面中的占比
     */
    private void adjustDronePosition(String locationDesc, int proportion) {
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();

        // 计算位置偏移量（-1.0到1.0之间的值，0表示中心）
        double horizontalOffset = calculateHorizontalOffset(locationDesc);

        double verticalOffset = calculateVerticalOffset(locationDesc);

        // 计算基于占比的接近程度（0-1之间，1表示非常近）
        double proximityFactor = calculateProximityFactor(proportion);

        // 根据偏移量和接近程度计算移动距离和方向
        double horizontalMoveDistance = calculateHorizontalMoveDistance(horizontalOffset, proximityFactor);
        double verticalMoveDistance = calculateHorizontalMoveDistance(verticalOffset, proximityFactor);
        double forwardMoveDistance = calculateForwardMoveDistance(proximityFactor);

        // 执行调整动作
        executeAdjustmentMovement(horizontalOffset, horizontalMoveDistance, verticalOffset, verticalMoveDistance, forwardMoveDistance, proximityFactor);
    }

    /**
     * 根据位置描述计算水平偏移量
     * @param locationDesc 位置描述（left, center, right）
     * @return 偏移量（-1.0到1.0之间，负值表示左侧，正值表示右侧）
     */
    private double calculateHorizontalOffset(String locationDesc) {
        switch (locationDesc) {
            case "left":
                return -0.7; // 左侧偏移
            case "right":
                return 0.7;  // 右侧偏移
            case "center":
                return 0.0;  // 居中
            default:
                return 0.0;  // 默认居中
        }
    }

    /**
     * 根据位置描述计算竖直偏移量
     * @param locationDesc 位置描述（left, center, right）
     * @return 偏移量（-1.0到1.0之间，负值表示左侧，正值表示右侧）
     */
    private double calculateVerticalOffset(String locationDesc) {
        switch (locationDesc) {
            case "backward":
                return -0.7; // 后方偏移
            case "forward":
                return 0.7;  // 前方偏移
            case "center":
                return 0.0;  // 居中
            default:
                return 0.0;  // 默认居中
        }
    }

    /**
     * 根据占比计算接近因子
     * @param proportion 目标占比
     * @return 接近因子（0-1之间，1表示非常近）
     */
    private double calculateProximityFactor(int proportion) {
        // 将占比转换为0-1之间的值
        double factor = proportion / 100.0;

        // 应用非线性变换，使接近因子在低占比时变化更快
        factor = Math.pow(factor, 0.7);

        return Math.min(1.0, factor);
    }

    /**
     * 计算水平移动距离
     * @param horizontalOffset 水平偏移量（-1.0到1.0）
     * @param proximityFactor 接近因子（0-1）
     * @return 移动距离（米）
     */
    private double calculateHorizontalMoveDistance(double horizontalOffset, double proximityFactor) {
        // 水平移动距离与偏移量成正比，与接近因子成反比
        // 当目标越接近时，移动越小心
        double baseDistance = Math.abs(horizontalOffset) * 2.0; // 基础距离
        double adjustedDistance = baseDistance * (1.0 - 0.7 * proximityFactor); // 根据接近程度调整

        // 确保移动距离在合理范围内
        return Math.max(MIN_MOVE_DISTANCE, Math.min(adjustedDistance, MAX_MOVE_DISTANCE));
    }

    /**
     * 计算前进移动距离
     * @param proximityFactor 接近因子（0-1）
     * @return 前进距离（米）
     */
    private double calculateForwardMoveDistance(double proximityFactor) {
        // 当目标还很远（占比小）时，前进距离较大
        // 当目标接近（占比大）时，前进距离减小
        double distanceRange = MAX_MOVE_DISTANCE - MIN_MOVE_DISTANCE;
        double moveDistance = MIN_MOVE_DISTANCE + distanceRange * (1.0 - proximityFactor);

        // 确保前进距离在合理范围内
        return Math.max(MIN_MOVE_DISTANCE, Math.min(moveDistance, MAX_MOVE_DISTANCE));
    }

    /**
     * 执行调整动作
     * @param horizontalOffset 水平偏移（-1.0到1.0）
     * @param horizontalMoveDistance 水平移动距离
     * @param forwardMoveDistance 前进距离
     * @param proximityFactor 接近因子
     */
    private void executeAdjustmentMovement(double horizontalOffset, double horizontalMoveDistance,
                                           double verticalOffset, double verticalMoveDistance,
                                           double forwardMoveDistance, double proximityFactor) {
        // 根据目标情况优化运动序列
        if (Math.abs(horizontalOffset) > 0.3) {
            // 目标不在中心，优先调整水平位置
            if (horizontalOffset < 0) {
                // 目标在左侧，向左移动
                callback.addChatMessage(Constant.OWNER_BOT,
                        String.format("%s在图像左侧，向左移动%.2f米", targetObjectType, horizontalMoveDistance));
                mSingletonVirtualStickExecutor.mGo(303, horizontalMoveDistance);
            } else {
                // 目标在右侧，向右移动
                callback.addChatMessage(Constant.OWNER_BOT,
                        String.format("%s在图像右侧，向右移动%.2f米", targetObjectType, horizontalMoveDistance));
                mSingletonVirtualStickExecutor.mGo(304, horizontalMoveDistance);
            }

            // 水平移动后短暂暂停，让无人机稳定
            SleepThread(500);
        } else if (Math.abs(verticalOffset) > 0.3){
            // 目标不在中心，优先调整水平位置
            if (verticalOffset < 0) {
                // 目标在后，向后移动
                callback.addChatMessage(Constant.OWNER_BOT,
                        String.format("%s在图像后，向后移动%.2f米", targetObjectType, verticalMoveDistance));
                mSingletonVirtualStickExecutor.mGo(302, verticalMoveDistance);
            } else {
                // 目标在前，向前移动
                callback.addChatMessage(Constant.OWNER_BOT,
                        String.format("%s在图像前，向前移动%.2f米", targetObjectType, verticalMoveDistance));
                mSingletonVirtualStickExecutor.mGo(301, verticalMoveDistance);
            }
            // 水平移动后短暂暂停，让无人机稳定
            SleepThread(500);
        } else if (proximityFactor < 0.6) {
            // 目标接近中心但距离较远，向前移动
            callback.addChatMessage(Constant.OWNER_BOT,
                    String.format("%s已大致位于中心，占比为%.0f%%，向前移动%.2f米靠近...",
                            targetObjectType, proximityFactor * 100, forwardMoveDistance));
            mSingletonVirtualStickExecutor.mGo(301, forwardMoveDistance);
            SleepThread(500);
        } else {
            // 目标基本居中且接近，微调位置
            if (proximityFactor < 0.8) {
                callback.addChatMessage(Constant.OWNER_BOT,
                        String.format("%s居中且接近，进行微调(占比%.0f%%)...", targetObjectType, proximityFactor * 100));
                mSingletonVirtualStickExecutor.mGo(301, MIN_MOVE_DISTANCE);
            } else {
                callback.addChatMessage(Constant.OWNER_BOT, String.format("%s已居中且足够接近，不需要移动。", targetObjectType));
            }
        }
    }

    //目标特征分析

    /**
     * 识别目标物体品牌或特征。
     * 如果使用 GPT，会调用 sendQuestionToGPT()；否则调用 sendQuestionToAPI()。
     */
    private void recognizeCarBrand(){
        // 1. 拍照
        Bitmap bitmap = mfpvTexture.getBitmap();
        if (bitmap == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 可能未准备好");
            return;
        }

        File brandImgFile = saveBitmapAsFile(bitmap, "frame.jpg");
        if (brandImgFile == null) {
            callback.addChatMessage(Constant.OWNER_BOT, String.format("拍照失败，无法识别%s...", targetObjectType));
            return;
        }

        // 2. 构造识别请求
        String brandPrompt = String.format("请识别图片中%s的品牌特征。请给出 JSON 输出，如 {\"brand_name\":\"品牌名称\", \"features\":\"特征描述\"}", targetObjectType);
        callback.addChatMessage(Constant.OWNER_BOT, String.format("正在识别%s特征，请稍候...", targetObjectType));

        try{
            // 3. 调用 GPT 或 API
            String gptResult = callback.sendQuestionToGPTSync(brandPrompt, brandImgFile, true);

            // 4. 解析响应结果
            JsonUtils.ParseResult brandParse = JsonUtils.robustJsonParser(gptResult);
            if (brandParse.getJsonData() == null) {
                callback.addChatMessage(Constant.OWNER_BOT, String.format("未能识别%s特征，JSON 数据为空。", targetObjectType));
                return;
            }

            String brandProcess = brandParse.getInferenceProcess();
            String brandName = brandParse.getJsonData().optString("brand_name", "未知品牌");
            String features = brandParse.getJsonData().optString("features", "");

            callback.addChatMessage(Constant.OWNER_BOT, String.format("%s识别推理过程: %s", targetObjectType, brandProcess));
            callback.addChatMessage(Constant.OWNER_BOT, String.format("识别到的品牌: %s", brandName));
            
            if (!features.isEmpty()) {
                callback.addChatMessage(Constant.OWNER_BOT, String.format("特征描述: %s", features));
            }

        } catch (Exception e) {
            callback.addChatMessage(Constant.OWNER_BOT, "解析结果时出错: " + e.getMessage());
        }
    }

    //辅助函数
    /**
     * 保存为图像文件
     * @param bitmap
     * @param filename
     * @return
     */
    private File saveBitmapAsFile(Bitmap bitmap, String filename) {
        File file = new File(callback.mgetCacheDir(), filename); // 保存到应用的缓存目录
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // 压缩并保存为 JPEG
        } catch (IOException e) {

            e.printStackTrace();
            return null;
        }
        return file;
    }

    /**
     * 获取当前帧图像
     */
    public File CaptureImage(){

//        Resources res = callback.mgetResources();
//        Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.search_frame1);
//        File imageFile = saveBitmapAsFile(bitmap,"frame1.jpg");

        Bitmap bitmap = mfpvTexture.getBitmap();
        if (bitmap == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 未准备好");
            return null;
        }

        File imageFile = saveBitmapAsFile(bitmap, IMAGE_FILE_NAME);
        if (imageFile == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "图片保存失败");
            return null;
        }
        return imageFile;
    }

    /**
     * 阻塞主线程
     */
    private void SleepThread(int time){
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.addChatMessage(Constant.OWNER_BOT, "线程被中断");
        }
    }

    //endregion

    //收集车辆信息

    /**
     * 收集目标的详细信息
     */
    public void getObjInformation(){
        //后退2.5米
        double distance = 2.5;

        //绕圈飞行
        flyWithCircle(distance);
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {
            if (downloadEvent.getError() != null) {
                Log.e(TAG, "onDownloadUpdate: 下载错误: " + downloadEvent.getError().getDescription());
            }
        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {
            
            if ((uploadEvent.getError() != null)) {
                Log.e(TAG, "onUploadUpdate: 上传错误: " + uploadEvent.getError().getDescription());
            }

            if (uploadEvent.getPreviousState() == WaypointMissionState.UPLOADING
                    && uploadEvent.getCurrentState() == WaypointMissionState.READY_TO_EXECUTE ) {
                // upload complete, can start mission
                // getWaypointMissionOperator().startMission();
                mWaypoint.canStartMission = true;
                Log.d(TAG, "onUploadUpdate: 航点任务上传完成，准备执行");
            }
            mWaypoint.startWaypointMission(mMissionOperator);
        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {
            Log.d(TAG, "onExecutionUpdate: 收到航点任务执行事件 - 当前航点索引: " + executionEvent.getProgress().targetWaypointIndex + 
                  ", 执行状态: " + executionEvent.getProgress().executeState.name());
            
            if (executionEvent.getError() != null) {
                Log.e(TAG, "onExecutionUpdate: 执行错误: " + executionEvent.getError().getDescription());
            }
        }

        @Override
        public void onExecutionStart() {
            Log.d(TAG, "onExecutionStart: 航点任务开始执行");
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "航点任务开始执行");
            });
        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            isend = true;
            Log.d(TAG, "onExecutionFinish: 航点任务执行完成" + (error == null ? "" : ", 错误: " + error.getDescription()));
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    //region 基于航点的绕圈飞行
    /**
     * 计算以给定中心点和半径的圆上均匀分布的n个点的经纬度坐标
     * @param centerLat 中心点纬度
     * @param centerLon 中心点经度
     * @param radiusInMeters 圆半径（米）
     * @param numberOfPoints 需要计算的点数量
     * @param initialBearing 初始方位角（度），0表示正北，90表示正东，以此类推
     * @return 包含所有点坐标的数组，每个点是一个double[2]数组，[0]为纬度，[1]为经度
     */
    public double[][] calculateCirclePoints(double centerLat, double centerLon,
                                            double radiusInMeters, int numberOfPoints,
                                            double initialBearing) {
        Log.d(TAG, "calculateCirclePoints: 开始计算圆形航点 - 中心点: [" + centerLat + ", " + centerLon +
                "], 半径: " + radiusInMeters + "米, 点数量: " + numberOfPoints +
                ", 初始方位角: " + initialBearing + "°");

        double[][] points = new double[numberOfPoints][2];

        // 计算每个点之间的角度间隔（弧度）
        double angleStep = 2 * Math.PI / numberOfPoints;
        Log.d(TAG, "calculateCirclePoints: 角度步进值: " + Math.toDegrees(angleStep) + "°");

        // 将初始方位角转换为弧度
        double bearingRad = Math.toRadians(initialBearing);

        for (int i = 0; i < numberOfPoints; i++) {
            // 计算当前点的方位角（弧度）
            double currentBearing = bearingRad + i * angleStep;

            // 确保方位角在 0 到 2π 之间
            while (currentBearing < 0) {
                currentBearing += 2 * Math.PI;
            }
            while (currentBearing >= 2 * Math.PI) {
                currentBearing -= 2 * Math.PI;
            }

            // 转换为度数，用于Utils.calcDestination方法
            double bearingDegrees = Math.toDegrees(currentBearing);

            Log.d(TAG, "calculateCirclePoints: 点 " + (i+1) + " 方位角: " + bearingDegrees + "°");

            // 计算目标点坐标
            double[] destination = Utils.calcDestination(centerLat, centerLon, bearingDegrees, radiusInMeters);

            // 存储结果
            points[i][0] = destination[0]; // 纬度
            points[i][1] = destination[1]; // 经度

            Log.d(TAG, "calculateCirclePoints: 点 " + (i+1) + " 坐标: [" + points[i][0] + ", " + points[i][1] + "]");
        }

        Log.d(TAG, "calculateCirclePoints: 圆形航点计算完成");
        return points;
    }

    /**
     * 绕圈飞行
     * @param r 圆形轨迹的半径（米）
     */
    private void flyWithCircle(double r){
        Log.d(TAG, "flyWithCircle: 开始绕圈飞行任务，半径: " + r + "米");
        
        //获取车辆上方的无人机位置
        droneLocation = callback.getDroneLocation();
        droneAlt = droneLocation.getAltitude();
        droneLat = droneLocation.getLatitude();
        droneLon = droneLocation.getLongitude();
        
        Log.d(TAG, "flyWithCircle: 无人机当前位置 - 纬度: " + droneLat + 
              ", 经度: " + droneLon + ", 高度: " + droneAlt + "米");

        //获取车辆的位置
        objAlt = 1;
        objLat = droneLat;
        objLon = droneLon;
        
        Log.d(TAG, "flyWithCircle: 目标物位置 - 纬度: " + objLat + 
              ", 经度: " + objLon + ", 高度: " + objAlt + "米");

        reduis = 2.5f;
        Context context = callback.getContext();
        if (context == null) {
            Log.e(TAG, "flyWithCircle: 上下文为空，无法获取虚拟摇杆控制实例");
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "错误: 上下文为空，无法执行后退操作");
            });
            return;
        }
        
        Log.d(TAG, "flyWithCircle: 无人机后退 " + r + "米以准备绕圈");
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
        mSingletonVirtualStickExecutor.mGo(302, r);

        //根据r 计算航点 绕圈飞行的航点坐标
        //初始化航点操作类
        Log.d(TAG, "flyWithCircle: 初始化航点任务");
        mWaypoint = new Waypointv1();
        mMissionOperator = getWaypointMissionOperator(mMissionOperator);
        if (mMissionOperator == null) {
            Log.e(TAG, "flyWithCircle: 无法获取航点任务操作器，任务取消");
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "错误: 无法获取航点任务操作器，任务取消");
            });
            return;
        }
        
        mMissionOperator.addListener(eventNotificationListener);
        Log.d(TAG, "flyWithCircle: 已添加航点任务监听器");

        //设置兴趣点
        mWaypoint.AddPointInterst(objLat,objLon);
        Log.d(TAG, "flyWithCircle: 已设置兴趣点 - 纬度: " + objLat + ", 经度: " + objLon);

        // 清空现有航点
        if (mWaypoint.waypointMissionBuilder != null) {
            Log.d(TAG, "flyWithCircle: 清空现有航点");
            mWaypoint.waypointMissionBuilder = null;
        }

        // 设置飞行高度（海拔高度）
        mWaypoint.altitude = (float) droneAlt;
        Log.d(TAG, "flyWithCircle: 设置飞行高度: " + mWaypoint.altitude + "米");

        // 定义航点数量
        int numberOfWaypoints = 10;
        Log.d(TAG, "flyWithCircle: 定义航点数量: " + numberOfWaypoints);

        // 计算圆上的点
        Log.d(TAG, "flyWithCircle: 开始计算圆上的点，使用初始方位角: " + callback.getHeading() + "°");
        double[][] waypoints = calculateCirclePoints(
            objLat, objLon,     // 中心点坐标
            r,                  // 半径
            numberOfWaypoints,  // 点数量
            callback.getHeading() // 初始方位角
        );
        Log.d(TAG, "flyWithCircle: 圆上点计算完成");

        // 添加所有航点
        int heading = (int) callback.getHeading();
        Log.d(TAG, "flyWithCircle: 开始添加航点");
        for (int i = 0; i < waypoints.length; i++) {
            double waypointLat = waypoints[i][0];
            double waypointLon = waypoints[i][1];
            
            // 添加航点
//            mWaypoint.AddWaypoint(waypointLat, waypointLon, callback.gerAltitude(), reduis);
            mWaypoint.AddWaypoint(waypointLat, waypointLon, callback.gerAltitude(),heading,1f,reduis);
            heading += (360/numberOfWaypoints);
            if(heading>180){
                heading = heading -180;
            }
            Log.d(TAG, "flyWithCircle: 添加航点 " + (i+1) + " - 纬度: " + waypointLat + 
                  ", 经度: " + waypointLon + ", 高度: " + callback.gerAltitude() + 
                  ", 半径: " + reduis);
            
            // 记录日志
            int finalI = i;
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT,
                        String.format("添加航点 %d: 纬度 %.6f, 经度 %.6f", finalI + 1, waypointLat, waypointLon));
            });
        }

        // 添加最后一个航点（返回起始点，形成闭环）
        double[] firstPoint = Utils.calcDestination(objLat, objLon, callback.getHeading(), r);
        mWaypoint.AddWaypoint(firstPoint[0], firstPoint[1], callback.gerAltitude(), reduis);
        Log.d(TAG, "flyWithCircle: 添加闭环航点 - 纬度: " + firstPoint[0] + 
              ", 经度: " + firstPoint[1] + ", 高度: " + callback.gerAltitude() + 
              ", 半径: " + reduis);

        // 执行航点任务
        Log.d(TAG, "flyWithCircle: 准备执行航点任务");
        startWaypointMission(r);
    }

    /**
     * 开始执行航点任务
     */
    private void startWaypointMission(double r) {
        Log.d(TAG, "startWaypointMission: 开始配置和执行航点任务，半径: " + r + "米");
        
        runOnUiThread(() -> {
            callback.addChatMessage(Constant.OWNER_BOT, "开始执行绕圈飞行任务...");
        });

        if (mMissionOperator != null) {
            try {
                // Get context from callback to ensure it's not null
                Context context = callback.getContext();
                if (context == null) {
                    Log.e(TAG, "startWaypointMission: 上下文为空，无法配置航点任务");
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "Error: Context is null");
                    });
                    return;
                }
                
                Log.d(TAG, "startWaypointMission: 设置航点任务飞行路径模式为CURVED");
                mWaypoint.waypointMissionFlightPathMode = WaypointMissionFlightPathMode.NORMAL;
                
                Log.d(TAG, "startWaypointMission: 设置航点任务朝向模式为TOWARD_POINT_OF_INTEREST");
                mWaypoint.mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                
                Log.d(TAG, "startWaypointMission: 开始配置航点任务");
                mWaypoint.configWayPointMission(mMissionOperator);
                
                Log.d(TAG, "startWaypointMission: 等待1秒");
                Thread.sleep(1000);
                
                Log.d(TAG, "startWaypointMission: 开始上传航点任务");
                mWaypoint.uploadWayPointMission(mMissionOperator);
            } catch (Exception e) {
                Log.e(TAG, "startWaypointMission: 创建任务异常: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "创建任务异常: " + e.getMessage());
                });
            }
        } else {
            Log.e(TAG, "startWaypointMission: 任务操作器为空，无法执行航点任务");
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "任务操作器或任务构建器未初始化");
            });
        }
    }

    /**
     * 获取航点控制权
     * @param instance
     * @return
     */
    public WaypointMissionOperator getWaypointMissionOperator(WaypointMissionOperator instance) {
        Log.d(TAG, "getWaypointMissionOperator: Called with instance " + (instance == null ? "null" : "not null"));

        if (instance == null) {
            Log.d(TAG, "getWaypointMissionOperator: Instance is null, requesting from DJISampleApplication");
            instance = DJISampleApplication.getWaypointMissionOperator();
            if (instance == null) {
                Log.e(TAG, "getWaypointMissionOperator: Failed to get instance from DJISampleApplication");
            } else {
                Log.d(TAG, "getWaypointMissionOperator: Successfully obtained instance from DJISampleApplication");
            }
        } else {
            Log.d(TAG, "getWaypointMissionOperator: Using existing instance");
        }

        return instance;
    }
    //endregion

    //region 基于HotMission的绕圈飞行
    /**
     * 获取航点控制权
     * @param instance
     * @return
     */
    public HotpointMissionOperator getHotMissionOperator(HotpointMissionOperator instance) {
        Log.d(TAG, "getHotMissionOperator: Called with instance " + (instance == null ? "null" : "not null"));

        if (instance == null) {
            Log.d(TAG, "getHotMissionOperator: Instance is null, requesting from DJISampleApplication");
            instance = DJISampleApplication.getHotMissionOperator();
            if (instance == null) {
                Log.e(TAG, "getHotMissionOperator: Failed to get instance from DJISampleApplication");
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "错误: 无法获取热点任务操作器");
                });
            } else {
                Log.d(TAG, "getHotMissionOperator: Successfully obtained instance from DJISampleApplication");
            }
        } else {
            Log.d(TAG, "getHotMissionOperator: Using existing instance");
        }

        return instance;
    }

    /**
     * 热点绕圈飞行
     * @param r 圆形轨迹的半径（米）
     */
     public void hotFlyCircle(double r){
        Log.d(TAG, "hotFlyCircle: [开始] 热点绕圈飞行任务初始化，半径: " + r + "米");
        
        try {
            //获取车辆上方的无人机位置
            Log.d(TAG, "hotFlyCircle: 正在获取无人机当前位置信息...");
            droneLocation = callback.getDroneLocation();
            if (droneLocation == null) {
                Log.e(TAG, "hotFlyCircle: 无人机位置数据为空!");
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "错误: 无法获取无人机位置数据");
                });
                return;
            }
            
            droneAlt = droneLocation.getAltitude();
            droneLat = droneLocation.getLatitude();
            droneLon = droneLocation.getLongitude();
            
            Log.d(TAG, "hotFlyCircle: 无人机当前位置 - 纬度: " + droneLat + 
                ", 经度: " + droneLon + ", 高度: " + droneAlt + "米");

            // 检查位置数据有效性
            if (droneLat == 0 && droneLon == 0) {
                Log.w(TAG, "hotFlyCircle: 警告 - 无人机位置数据可能无效 (0,0)");
            }

            //获取车辆的位置（此处直接使用无人机当前位置作为目标位置）
            Log.d(TAG, "hotFlyCircle: 设置目标物位置（使用无人机当前位置）");
            objAlt = 10;
            objLat = droneLat;
            objLon = droneLon;
            
            Log.d(TAG, "hotFlyCircle: 目标物位置 - 纬度: " + objLat + 
                ", 经度: " + objLon + ", 高度: " + objAlt + "米");
            
            Log.d(TAG, "hotFlyCircle: 准备调用热点圆形绕飞任务方法，参数：纬度=" + objLat + 
                ", 经度=" + objLon + ", 高度=" + objAlt + ", 半径=" + r + ", 角速度=2.0");
            
            exechotpointmission(objLat, objLon, objAlt, r, 20f);
            
            Log.d(TAG, "hotFlyCircle: [完成] 热点绕圈飞行方法调用完成");
            
        } catch (Exception e) {
            Log.e(TAG, "hotFlyCircle: 发生异常: " + e.getMessage(), e);
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "执行热点圆形绕飞过程中发生错误: " + e.getMessage());
            });
        }
    }

    /**
     * 执行热点圆形绕飞任务
     */
    protected void exechotpointmission(double hotlat, double hotlng, double hotalt, double hotr, float hotw) {
        Log.d(TAG, "exechotpointmission: [开始] 执行热点圆形绕飞任务");
        Log.d(TAG, "exechotpointmission: 任务参数 - 纬度: " + hotlat + 
              ", 经度: " + hotlng + ", 高度: " + hotalt + 
              ", 半径: " + hotr + ", 角速度: " + hotw);
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 圆形绕飞
                try {
                    // 设置云台的俯仰角
                    gimbalControl = new gimbalControl();
                    gimbalControl.pitchGimbalAbsolute(-55);

                    // 圆形绕飞
                    Log.d(TAG, "exechotpointmission: 创建热点任务对象");
                    HotpointMission hotpointMission = new HotpointMission();
                    
                    Log.d(TAG, "exechotpointmission: 设置热点位置");
                    LocationCoordinate2D hotpoint = new LocationCoordinate2D(hotlat, hotlng);
                    hotpointMission.setHotpoint(hotpoint);
                    
                    Log.d(TAG, "exechotpointmission: 设置飞行高度: " + hotalt + "米");
                    hotpointMission.setAltitude(hotalt);
                    
                    Log.d(TAG, "exechotpointmission: 设置绕飞半径: " + hotr + "米");
                    hotpointMission.setRadius(hotr);
                    
                    Log.d(TAG, "exechotpointmission: 设置角速度: " + hotw + "度/秒");
                    hotpointMission.setAngularVelocity(hotw);
                    
                    Log.d(TAG, "exechotpointmission: 设置起始点为最近点");
                    HotpointStartPoint startPoint = HotpointStartPoint.NEAREST;
                    hotpointMission.setStartPoint(startPoint);
                    
                    Log.d(TAG, "exechotpointmission: 设置航向为朝向兴趣点");
                    HotpointHeading heading = HotpointHeading.TOWARDS_HOT_POINT;
                    hotpointMission.setHeading(heading);
                    hotpointMission.setClockwise(true);
                    
                    Log.d(TAG, "exechotpointmission: 等待5秒钟准备执行任务");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "exechotpointmission: 等待被中断", e);
                        e.printStackTrace();
                    }
        
                    Log.d(TAG, "exechotpointmission: 获取热点任务操作器");
                    hotpointMissionOperator = getHotMissionOperator(hotpointMissionOperator);
                    
                    if (hotpointMissionOperator == null) {
                        Log.e(TAG, "exechotpointmission: 热点任务操作器为空，无法执行任务");
                        runOnUiThread(() -> {
                            callback.addChatMessage(Constant.OWNER_BOT, "错误: 热点任务操作器为空，无法执行任务");
                        });
                        return;
                    }
                    
                    Log.d(TAG, "exechotpointmission: 设置热点任务监听器");
                    setUpHotpointListener();
        
                    Log.d(TAG, "exechotpointmission: 开始执行热点任务");
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "开始执行热点圆形绕飞任务，半径: " + hotr + "米");
                    });
                    
                    hotpointMissionOperator.startMission(hotpointMission, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if(djiError == null) {
                                Log.d(TAG, "onResult: 热点任务成功启动");
                                runOnUiThread(() -> {
                                    callback.addChatMessage(Constant.OWNER_BOT, "热点圆形绕飞任务成功启动");
                                    showToast("热点任务已成功启动");
                                });
                            } else {
                                Log.e(TAG, "onResult: 热点任务执行失败: " + djiError.getDescription());
                                Log.e(TAG, "onResult: 错误代码: " + djiError.getErrorCode());
                                runOnUiThread(() -> {
                                    callback.addChatMessage(Constant.OWNER_BOT, 
                                        "热点任务启动失败: " + djiError.getDescription() + 
                                        " (错误代码: " + djiError.getErrorCode() + ")");
                                    showToast("热点任务启动失败，查看日志了解详情");
                                });
                            }
                        }
                    });
                    
                    Log.d(TAG, "exechotpointmission: [完成] 热点任务启动命令已发送");
                    
                } catch (Exception e) {
                    Log.e(TAG, "exechotpointmission: 执行过程中发生异常", e);
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "执行热点圆形绕飞任务时发生异常: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    /**
     * 设置热点圆形绕飞任务Listener
     */
    private void setUpHotpointListener() {
        Log.d(TAG, "setUpHotpointListener: [开始] 设置热点任务监听器");
        
        hotpointlistener = new HotpointMissionOperatorListener() {
            @Override
            public void onExecutionUpdate(@NonNull HotpointMissionEvent hotpointMissionEvent) {
                Log.d(TAG, "onExecutionUpdate: 收到热点任务更新事件");

                
                // 检查是否有错误
                if (hotpointMissionEvent.getError() != null) {
                    Log.e(TAG, "onExecutionUpdate: 执行中出现错误: " + 
                          hotpointMissionEvent.getError().getDescription());
                    Log.e(TAG, "onExecutionUpdate: 错误代码: " + 
                          hotpointMissionEvent.getError().getErrorCode());
                }
//                showToast("Execution update!");
            }

            @Override
            public void onExecutionStart() {
                Log.d(TAG, "onExecutionStart: 热点任务开始执行");
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "热点圆形绕飞任务开始执行");
                });
                showToast("Execution started!");
            }

            @Override
            public void onExecutionFinish(@Nullable DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "onExecutionFinish: 热点任务成功完成");
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "热点圆形绕飞任务已成功完成");
                    });
                } else {
                    Log.e(TAG, "onExecutionFinish: 热点任务完成但有错误: " + djiError.getDescription());
                    Log.e(TAG, "onExecutionFinish: 错误代码: " + djiError.getErrorCode());
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, 
                            "热点圆形绕飞任务完成但有错误: " + djiError.getDescription());
                    });
                }
                showToast("Execution finished!");
            }
        };

        if (hotpointMissionOperator != null && hotpointlistener != null) {
            Log.d(TAG, "setUpHotpointListener: 添加监听器到热点任务操作器");
            hotpointMissionOperator.addListener(hotpointlistener);
            Log.d(TAG, "setUpHotpointListener: 监听器添加成功");
        } else {
            Log.e(TAG, "setUpHotpointListener: 无法添加监听器 - " + 
                  "热点任务操作器为" + (hotpointMissionOperator == null ? "null" : "not null") + 
                  ", 监听器为" + (hotpointlistener == null ? "null" : "not null"));
        }
        
        Log.d(TAG, "setUpHotpointListener: [完成] 热点任务监听器设置完成");
    }
    
    /**
     * 停止当前热点任务
     */
    public void stopHotpointMission() {
        Log.d(TAG, "stopHotpointMission: [开始] 尝试停止热点任务");
        
        if (hotpointMissionOperator == null) {
            Log.e(TAG, "stopHotpointMission: 热点任务操作器为空，无法停止任务");
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "错误: 热点任务操作器为空，无法停止任务");
            });
            return;
        }
        
        hotpointMissionOperator.stop(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "stopHotpointMission: 热点任务成功停止");
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "热点圆形绕飞任务已成功停止");
                        showToast("热点任务已停止");
                    });
                } else {
                    Log.e(TAG, "stopHotpointMission: 停止热点任务失败: " + djiError.getDescription());
                    Log.e(TAG, "stopHotpointMission: 错误代码: " + djiError.getErrorCode());
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, 
                            "停止热点任务失败: " + djiError.getDescription());
                        showToast("停止热点任务失败");
                    });
                }
            }
        });
        
        Log.d(TAG, "stopHotpointMission: [完成] 停止任务命令已发送");
    }
    //endregion

    /**
     * 停止所有操作和线程
     * 该方法会停止所有正在进行的操作，包括虚拟摇杆控制、热点任务和搜索线程
     */
    public void stopAllOperations() {
        Log.d(TAG, "stopAllOperations: [开始] 停止所有操作和线程");
        
        // 标记搜索结束，以便递归操作可以退出
        isfindCar = true;
        
        // 关闭线程池
        if (executorService != null && !executorService.isShutdown()) {
            try {
                Log.d(TAG, "stopAllOperations: 关闭线程池");
                executorService.shutdownNow();
            } catch (Exception e) {
                Log.e(TAG, "stopAllOperations: 关闭线程池时出错: " + e.getMessage(), e);
            }
        }
        
        // 停止虚拟摇杆控制
        try {
            Log.d(TAG, "stopAllOperations: 停止虚拟摇杆控制");
            if (mSingletonVirtualStickExecutor != null) {
                mSingletonVirtualStickExecutor.mStop();
            }
        } catch (Exception e) {
            Log.e(TAG, "stopAllOperations: 停止虚拟摇杆控制时出错: " + e.getMessage(), e);
        }
        
        // 停止热点任务
        try {
            Log.d(TAG, "stopAllOperations: 停止热点任务");
            if (hotpointMissionOperator != null) {
                stopHotpointMission();
            }
        } catch (Exception e) {
            Log.e(TAG, "stopAllOperations: 停止热点任务时出错: " + e.getMessage(), e);
        }
        
        // 停止航点任务
        try {
            Log.d(TAG, "stopAllOperations: 停止航点任务");
            if (mMissionOperator != null && mMissionOperator.getCurrentState() == WaypointMissionState.EXECUTING) {
                mMissionOperator.stopMission(error -> {
                    if (error == null) {
                        Log.d(TAG, "stopAllOperations: 航点任务已成功停止");
                        runOnUiThread(() -> {
                            callback.addChatMessage(Constant.OWNER_BOT, "航点任务已成功停止");
                        });
                    } else {
                        Log.e(TAG, "stopAllOperations: 停止航点任务失败: " + error.getDescription());
                        runOnUiThread(() -> {
                            callback.addChatMessage(Constant.OWNER_BOT, "停止航点任务失败: " + error.getDescription());
                        });
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "stopAllOperations: 停止航点任务时出错: " + e.getMessage(), e);
        }
        
        // 移除所有监听器
        try {
            Log.d(TAG, "stopAllOperations: 移除热点任务监听器");
            if (hotpointMissionOperator != null && hotpointlistener != null) {
                hotpointMissionOperator.removeListener(hotpointlistener);
            }
            
            Log.d(TAG, "stopAllOperations: 移除航点任务监听器");
            if (mMissionOperator != null) {
                mMissionOperator.removeListener(eventNotificationListener);
            }
        } catch (Exception e) {
            Log.e(TAG, "stopAllOperations: 移除监听器时出错: " + e.getMessage(), e);
        }
        
        // 通知用户所有操作已停止
        runOnUiThread(() -> {
            callback.addChatMessage(Constant.OWNER_BOT, String.format("已停止所有与%s相关的操作", targetObjectType));
            showToast("所有操作已停止");
        });
        
        Log.d(TAG, "stopAllOperations: [完成] 所有操作和线程已停止");
    }
}
