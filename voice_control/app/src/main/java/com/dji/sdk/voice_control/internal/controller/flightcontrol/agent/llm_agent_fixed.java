package com.dji.sdk.voice_control.internal.controller.flightcontrol.agent;

import static com.dji.sdk.voice_control.internal.djidemo.utils.ToastUtils.showToast;
import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.TextureView;

import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.ControlActivity;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.djitool.gimbal.gimbalControl;
import com.dji.sdk.voice_control.internal.controller.djitool.waypoint.Waypoint;
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

import dji.common.error.DJIWaypointV2Error;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.mission.waypointv2.WaypointV2MissionDownloadEvent;
import dji.common.mission.waypointv2.WaypointV2MissionExecutionEvent;
import dji.common.mission.waypointv2.WaypointV2MissionState;
import dji.common.mission.waypointv2.WaypointV2MissionUploadEvent;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointV2MissionOperator;
import dji.common.mission.waypointv2.WaypointV2;
import dji.common.mission.waypointv2.WaypointV2Mission;
import dji.common.mission.waypointv2.WaypointV2MissionTypes;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.model.LocationCoordinate2D;
import dji.sdk.mission.waypoint.WaypointV2MissionOperatorListener;
import dji.sdk.sdkmanager.DJISDKManager;

public class llm_agent_fixed {

    private FlightController mFlightController;
    private CommandInterpreter mCI;
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    private TextureView mfpvTexture;
    private ControlActivityCallback callback;


    //region agent 数据结构
    private static final String AGENT_URL = "http://122.207.106.69:25130/chat";
    private static final String TEMPLATE="Please answer the following question: {question}";
    // 常量定义
    private static final int MAX_SEARCH_ATTEMPTS = 100;
    private static final int SLEEP_BETWEEN_SEARCH_MS = 2000;
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

    //提示词
    private String direction_prompt = "请分析图像，回答以下问题。首先，详细描述您的推理过程。然后，将您的答案以JSON格式输出。\n" +
            "\n" +
            "推理过程：\n" +
            "- 描述您如何判断图中是否有黑色轿车,置信度水平如何。\n" +
            "- 解释您对黑色轿车位置（左、中、右）的判断依据。\n" +
            "- 描述您如何估算黑色轿车占据图像的比例。\n" +
            "\n" +
            "请在推理过程之后，输出JSON格式的答案：\n" +
            "\n" +
            "{\n" +
            "  \"has_white_car\": 布尔值（true或false），\n" +
            "  \"confidence_percentage\": 整数，范围0-100，表示您认为图中有黑色轿车的把握，\n" +
            "  \"location_description\": \"字符串，'left'、'center'或'right'，描述黑色轿车在图像中的位置\",\n" +
            "  \"estimated_proportion_percentage\": 整数，范围0-100，估计黑色轿车占据图像的比例，\n" +
            "}\n" +
            "\n" +
            "**注意：**\n" +
            "- 请先输出推理过程，然后在下一行输出JSON对象。\n" +
            "- 不要在JSON对象之外添加额外的文本或注释。\n" +
            "- 请避免使用诸如抱歉，我无法查看或分析图片内容的句子，尽可能基于图像提供回答。\n" +
            "- 只需要判断目标在图像的左、右或者中间，不要回复类似左中(center-left)的回答。\n" +
            "- 请注意轿车通常具有完整黑色轿车轮廓。";
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
    private Waypoint mWaypoint;
    private WaypointV2MissionOperator mMissionOperator;
    private gimbalControl gimbalControl;

    private boolean isend = false;
    //endregion


    //构造函数
    public llm_agent_fixed(
            CommandInterpreter commandInterpreter,
            FlightController flightController,
            TextureView textureView,
            ControlActivityCallback callback
    ){
        this.mCI = commandInterpreter;
        this.mFlightController = flightController;
        this.mfpvTexture = textureView;
        this.callback = callback;

        gimbalControl = new gimbalControl();
    }


    //region Agent控制

    /**
     * 入口函数
     */
    public void agentFindCar() {
//        // 初始化虚拟摇杆执行器
//        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
        // 开启新线程
        new Thread(() -> {
            try {
                //起飞
                if(!callback.getisFlying()){
                    mCI.mTakeoff();
                }
                SleepThread(SLEEP_BETWEEN_SEARCH_MS);

                //设置一个合理的飞行高度
                //向上飞8米
                LocationCoordinate3D dronelocations = callback.getDroneLocation();
                if(dronelocations.getAltitude() < 3 ){
                    mSingletonVirtualStickExecutor.mUp(3);
                    SleepThread(SLEEP_BETWEEN_SEARCH_MS);
                }
                //开始锁定目标
                performSearch(1, MAX_SEARCH_ATTEMPTS, COMMAND_UP_ANGLE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    //搜索

    /**
     * 在场景中自动搜索车辆
     * @return
     */
    private boolean doSearch(int attemptIndex) throws Exception {
        // 如果超出最大次数，就结束
        if (attemptIndex >= MAX_SEARCH_ATTEMPTS) {
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "多次搜索仍未找到车辆。");
            });
            return false;
        }
        final boolean[] result = { false }; // 存放是否找到车
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
            boolean hasWhiteCar = parseResult.getJsonData().optBoolean("has_white_car", false);
            int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);

            if (hasWhiteCar && confidence >= 80) {
                result[0] = true;
                response += "\n车辆已锁定!";
                String finalResponse = response;
                runOnUiThread(() -> {callback.addChatMessage(Constant.OWNER_BOT, finalResponse);});
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Close_to();
                    }
                }).start();
            } else {
                response += "\n未能识别到目标车辆，继续搜索...";
                String finalResponse1 = response;
                runOnUiThread(() -> {callback.addChatMessage(Constant.OWNER_BOT, finalResponse1);});
                // 转动视角
                MyVirtualStickExecutor executor = MyVirtualStickExecutor.getUniqueInstance();
                executor.mTurn(303, 10);
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
     * @param ascendHeight   每次上升的高度
     */
    private void performSearch(int currentAttempt, int maxAttempts, int ascendHeight) throws Exception {
        // 每次旋转角度
        final int ROTATION_ANGLE = 45;
        // 当前高度的旋转次数 (360度 / 45度 = 8次)
        final int MAX_ROTATIONS = 8;
        
        // 执行360度环视搜索
        performRotationalSearch(currentAttempt, maxAttempts, ascendHeight, 0, MAX_ROTATIONS, ROTATION_ANGLE);
    }
    
    /**
     * 执行旋转式搜索，先旋转一周，如果没找到再上升高度
     * 
     * @param currentAttempt 当前高度尝试次数
     * @param maxAttempts 最大高度尝试次数
     * @param ascendHeight 每次上升高度
     * @param currentRotation 当前旋转次数
     * @param maxRotations 最大旋转次数(一般为8，对应360度)
     * @param rotationAngle 每次旋转角度
     */
    private void performRotationalSearch(
            int currentAttempt, 
            int maxAttempts, 
            int ascendHeight, 
            int currentRotation, 
            int maxRotations, 
            int rotationAngle) throws Exception {
        
        // 当前位置执行搜索
        boolean isFound = doSearch(0);
        
        // 如果找到目标，停止搜索
        if (isFound) {
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "车辆已锁定！"));
//            mSingletonVirtualStickExecutor.mStop();
            return;
        }
        
        // 如果已经旋转完一周仍未找到目标
        if (currentRotation >= maxRotations) {
            // 已经达到最大尝试次数，结束搜索
            if (currentAttempt >= maxAttempts) {
                runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, 
                    "已完成" + maxAttempts + "次高度搜索，共" + (maxRotations * maxAttempts) + "次扫描，未找到车辆。"));
                mSingletonVirtualStickExecutor.mStop();
                return;
            }
            
            // 上升到新高度
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, 
                String.format("已旋转360度未找到车辆，当前为第%d次搜索，上升%d米继续...", 
                currentAttempt + 1, ascendHeight)));
            
            mSingletonVirtualStickExecutor.mUp(ascendHeight);
            SleepThread(SLEEP_BETWEEN_SEARCH_MS);
            
            // 在新高度开始新一轮360度搜索
            performRotationalSearch(currentAttempt + 1, maxAttempts, ascendHeight, 0, maxRotations, rotationAngle);
        } else {
            // 继续旋转搜索
            runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, 
                String.format("第%d次高度，第%d次旋转搜索未找到车辆，旋转%d度继续搜索...", 
                currentAttempt + 1, currentRotation + 1, rotationAngle)));
            
            // 旋转无人机
            mSingletonVirtualStickExecutor.mTurn(303, rotationAngle);
            SleepThread(SLEEP_BETWEEN_SEARCH_MS); // 等待旋转和稳定
            
            // 在新角度继续搜索
            performRotationalSearch(currentAttempt, maxAttempts, ascendHeight, currentRotation + 1, maxRotations, rotationAngle);
        }
    }


    //靠近

    /**
     * 根据识别到的车辆信息，进行"靠近"操作。
     */
    public void Close_to() {
        performCloseToSearch(1, MAX_SEARCH_ATTEMPTS);
    }

    /**
     * 递归执行靠近搜索，直到满足条件或达到最大尝试次数
     */
    private void performCloseToSearch(int currentAttempt, int maxAttempts) {
        if( currentAttempt>maxAttempts ){
            recognizeCarBrand();
            return;
        }
        if (isfindCar) {
            return;
        }

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
                boolean has_car = parseResult.getJsonData().optBoolean("has_white_car", false);

                if(has_car && confidence>=80){
                    callback.addChatMessage(Constant.OWNER_BOT,
                            String.format("开始靠近车辆 —— 位置: %s, 置信度: %d%%, 占比: %d%%",
                                    locationDesc, confidence, proportion)
                    );

                    // 调整无人机位置
                    adjustDronePosition(locationDesc, proportion);
                } else {
                    isfindCar = true;
//                    recognizeCarBrand();
                    getObjInformation();
                    callback.addChatMessage(Constant.OWNER_BOT, "靠近车辆完毕。");
                }
            }
        } catch (Exception e) {
            callback.addChatMessage(Constant.OWNER_BOT, "解析结果时出错: " + e.getMessage());
        }
        if (!isfindCar) {
            // 递归调用，继续靠近搜索
            performCloseToSearch(currentAttempt+1,maxAttempts);
        }
    }

    /**
     * 调整无人机的位置或视角，使车辆更居中。
     * 当车辆已在中心 (center) 时，若占比 < 阈值，则向前移动一定距离。
     * @param locationDesc 车辆在画面中的位置描述 (left/right/center)
     * @param proportion   车辆在画面中的占比
     */
    private void adjustDronePosition(String locationDesc, int proportion) {
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();

        // 计算位置偏移量（-1.0到1.0之间的值，0表示中心）
        double horizontalOffset = calculateHorizontalOffset(locationDesc);
        
        // 计算基于占比的接近程度（0-1之间，1表示非常近）
        double proximityFactor = calculateProximityFactor(proportion);
        
        // 根据偏移量和接近程度计算移动距离和方向
        double horizontalMoveDistance = calculateHorizontalMoveDistance(horizontalOffset, proximityFactor);
        double forwardMoveDistance = calculateForwardMoveDistance(proximityFactor);

        // 执行调整动作
        executeAdjustmentMovement(horizontalOffset, horizontalMoveDistance, forwardMoveDistance, proximityFactor);
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
                                          double forwardMoveDistance, double proximityFactor) {
        // 根据目标情况优化运动序列
        if (Math.abs(horizontalOffset) > 0.3) {
            // 目标不在中心，优先调整水平位置
            if (horizontalOffset < 0) {
                // 目标在左侧，向左移动
                callback.addChatMessage(Constant.OWNER_BOT, 
                    String.format("车辆在图像左侧，向左移动%.2f米", horizontalMoveDistance));
                mSingletonVirtualStickExecutor.mGo(303, horizontalMoveDistance);
            } else {
                // 目标在右侧，向右移动
                callback.addChatMessage(Constant.OWNER_BOT, 
                    String.format("车辆在图像右侧，向右移动%.2f米", horizontalMoveDistance));
                mSingletonVirtualStickExecutor.mGo(304, horizontalMoveDistance);
            }
            
            // 水平移动后短暂暂停，让无人机稳定
            SleepThread(500);
        } else if (proximityFactor < 0.6) {
            // 目标接近中心但距离较远，向前移动
            callback.addChatMessage(Constant.OWNER_BOT, 
                String.format("车辆已大致位于中心，占比为%.0f%%，向前移动%.2f米靠近...", 
                proximityFactor * 100, forwardMoveDistance));
            mSingletonVirtualStickExecutor.mGo(301, forwardMoveDistance);
        } else {
            // 目标基本居中且接近，微调位置
            if (proximityFactor < 0.8) {
                callback.addChatMessage(Constant.OWNER_BOT, 
                    String.format("车辆居中且接近，进行微调(占比%.0f%%)...", proximityFactor * 100));
                mSingletonVirtualStickExecutor.mGo(301, MIN_MOVE_DISTANCE);
            } else {
                callback.addChatMessage(Constant.OWNER_BOT, "车辆已居中且足够接近，不需要移动。");
            }
        }
    }

    //目标特征分析

    /**
     * 拍照并识别车标品牌。
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
            callback.addChatMessage(Constant.OWNER_BOT, "拍照失败，无法识别车标...");
            return;
        }

        // 2. 构造识别请求
        String brandPrompt = "请识别图片中黑色轿车的车标品牌。请给出 JSON 输出，如 {\"brand_name\":\"Toyota\"}";
        callback.addChatMessage(Constant.OWNER_BOT, "正在识别车标，请稍候...");

        try{
            // 3. 调用 GPT 或 API
            String gptResult = callback.sendQuestionToGPTSync(brandPrompt, brandImgFile, true);

            // 4. 解析响应结果
            JsonUtils.ParseResult brandParse = JsonUtils.robustJsonParser(gptResult);
            if (brandParse.getJsonData() == null) {
                callback.addChatMessage(Constant.OWNER_BOT, "未能识别车标，JSON 数据为空。");
                return;
            }

            String brandProcess = brandParse.getInferenceProcess();
            String brandName = brandParse.getJsonData().optString("brand_name", "未知品牌");

            callback.addChatMessage(Constant.OWNER_BOT, "车标识别推理过程: " + brandProcess);
            callback.addChatMessage(Constant.OWNER_BOT, "识别到的品牌: " + brandName);

        } catch (Exception e) {
            callback.addChatMessage(Constant.OWNER_BOT, "解析结果时出错: " + e.getMessage());
        }
    }

    //收集车辆信息

    /**
     * 收集目标的详细信息
     */
    private void getObjInformation(){
        //后退2.5米
        double distance = 2.5;
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
        mSingletonVirtualStickExecutor.mGo(302,distance);

        //绕圈飞行
        flyWithCircle(distance);


    }

    private WaypointV2MissionOperatorListener eventNotificationListener = new WaypointV2MissionOperatorListener() {

        @Override
        public void onDownloadUpdate(WaypointV2MissionDownloadEvent waypointV2MissionDownloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointV2MissionUploadEvent uploadEvent) {
            if ((uploadEvent.getError() != null)) {
                // deal with the progress or the error info
            }

            if (uploadEvent.getCurrentState() == WaypointV2MissionState.READY_TO_EXECUTE) {
                // Can upload actions in it.
                // getWaypointMissionOperator().uploadWaypointActions();
            }
            if (uploadEvent.getPreviousState() == WaypointV2MissionState.UPLOADING
                    && uploadEvent.getCurrentState() == WaypointV2MissionState.READY_TO_EXECUTE ) {
            }


            mWaypoint.startWaypointMission(mMissionOperator);
        }

        @Override
        public void onExecutionUpdate(WaypointV2MissionExecutionEvent waypointV2MissionExecutionEvent) {

        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(DJIWaypointV2Error djiWaypointV2Error) {
            isend = true;
        }

        @Override
        public void onExecutionStopped() {

        }
    };

    /**
     * 绕圈飞行
     * @param r 圆形轨迹的半径（米）
     */
    private void flyWithCircle(double r){
        //获取车辆上方的无人机位置
        droneLocation = callback.getDroneLocation();
        droneAlt = droneLocation.getAltitude();
        droneLat = droneLocation.getLatitude();
        droneLon = droneLocation.getLongitude();

        //获取车辆的位置
        objAlt = 1;
        objLat = droneLat;
        objLon = droneLon;

        //根据r 计算航点 绕圈飞行的航点坐标
        //初始化航点操作类
        mWaypoint = new Waypoint();
        mMissionOperator = getWaypointMissionOperator(mMissionOperator);
        mMissionOperator.addWaypointEventListener(eventNotificationListener);

        // 清空现有航点
        if (mWaypoint.waypointMissionBuilder != null) {
            mWaypoint.waypointMissionBuilder = null;
        }

        // 设置飞行高度（海拔高度）
        mWaypoint.altitude = (float) droneAlt;
        
        // 定义航点数量 - 8个点可以形成一个较为平滑的圆形
        int numberOfWaypoints = 6;

        float inital_angle = (float) (180 - (360 / numberOfWaypoints)) /2;
        float normal_angle = (float) (360 / numberOfWaypoints);
        float inital_bearing = callback.getHeading();

        // 计算并添加每个航点
        for (int i = 0; i < numberOfWaypoints; i++) {
            double bearing = 0;

            if(i == 0) {
                bearing = callback.getHeading() + inital_angle;
            } else{
                bearing -= normal_angle;
            }
            // 使用Utils工具类计算目标坐标
            double[] destination = Utils.calcDestination(objLat, objLon, bearing, r);
            double waypointLat = destination[0];
            double waypointLon = destination[1];
            
            // 添加航点
            mWaypoint.AddWaypoint(waypointLat, waypointLon);
            
            // 记录日志
            int finalI = i;
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, 
                    String.format("添加航点 %d: 纬度 %.6f, 经度 %.6f", finalI +1, waypointLat, waypointLon));
            });
        }
        
        // 添加最后一个航点（返回起始点，形成闭环）
        double[] firstPoint = Utils.calcDestination(objLat, objLon, inital_bearing, r);
        mWaypoint.AddWaypoint(firstPoint[0], firstPoint[1]);
        
        // 执行航点任务
        startWaypointMission(r);

        new Thread(() -> {
            try{
                //调整云台位姿定时任务，在绕圈飞行过程中，每5秒调整一次云台位姿
                while(!isend){
                    adjustGimbal();
                    SleepThread(5000);
                }
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "绕圈飞行过程中出错: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 开始执行航点任务
     */
    private void startWaypointMission(double r) {
        runOnUiThread(() -> {
            callback.addChatMessage(Constant.OWNER_BOT, "开始执行绕圈飞行任务...");
        });
        
        if (mMissionOperator != null && mWaypoint.waypointMissionBuilder != null) {
            try {
                mWaypoint.configWayPointMission(mMissionOperator);
                mWaypoint.uploadWayPointMission(mMissionOperator);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "创建任务异常: " + e.getMessage());
                });
            }
        } else {
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "任务操作器或任务构建器未初始化");
            });

            // 备用方案：如果无法使用航点任务，则使用虚拟摇杆进行简单圆形飞行
            performManualCircularFlight(r);
        }
    }
    
    /**
     * 使用虚拟摇杆执行简单的圆形飞行（备用方案）
     * @param radius 圆形半径
     */
    private void performManualCircularFlight(double radius) {
        runOnUiThread(() -> {
            callback.addChatMessage(Constant.OWNER_BOT, "使用手动控制模式执行绕圈飞行...");
        });
        
        new Thread(() -> {
            try {
                // 定义飞行段数
                int segments = 8;
                double angleIncrement = 360.0 / segments;
                
                // 从北方向开始
                double currentBearing = 0;
                
                // 执行每个飞行段
                for (int i = 0; i < segments; i++) {
                    // 计算目标方向（顺时针旋转）
                    double targetBearing = currentBearing + angleIncrement;
                    
                    // 转向
                    mSingletonVirtualStickExecutor.mTurn(303, (int)(targetBearing - currentBearing));
                    SleepThread(2000); // 等待无人机转向
                    
                    // 计算弧长
                    double arcLength = 2 * Math.PI * radius / segments;
                    
                    // 向前飞行弧长距离
                    mSingletonVirtualStickExecutor.mGo(301, arcLength);
                    SleepThread(3000); // 等待无人机到达位置
                    
                    // 更新当前方向
                    currentBearing = targetBearing;
                }
                
                // 完成圆形飞行后开始车辆识别
                recognizeCarBrand();
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "手动圆形飞行过程中出错: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 实时调整云台位姿，使其始终对准车辆
     * 根据无人机与车辆的相对位置计算云台的绝对角度
     */
    @SuppressLint("DefaultLocale")
    private void adjustGimbal() {
        try {
            // 1. 获取当前无人机位置
            LocationCoordinate3D currentDroneLocation = callback.getDroneLocation();
            double droneLat = currentDroneLocation.getLatitude();
            double droneLon = currentDroneLocation.getLongitude();
            double droneAlt = currentDroneLocation.getAltitude();
            
            // 2. 计算从无人机到车辆的方位角（航向角）
            double bearingToVehicle = Utils.calcBearing(droneLat, droneLon, objLat, objLon);
            
            // 3. 获取无人机当前航向
            float droneHeading = callback.getHeading();
            
            // 4. 计算云台需要的yaw角度（相对于无人机航向）
            // 云台yaw需要补偿无人机航向，使其始终指向车辆
            float yawAngle = (float)(bearingToVehicle - droneHeading);
            
            // 归一化角度到 -180° 到 180° 范围
            while (yawAngle > 180) yawAngle -= 360;
            while (yawAngle < -180) yawAngle += 360;
            
            // 5. 计算俯仰角（pitch）
            // 计算水平距离
            double horizontalDistance = Utils.calcDistance(droneLat, droneLon, objLat, objLon);
            
            // 计算高度差
            double heightDifference = droneAlt - objAlt;
            
            // 计算俯仰角（负值表示向下）
            // tan(pitch) = 高度差 / 水平距离
            float pitchAngle = (float) -Math.toDegrees(Math.atan2(heightDifference, horizontalDistance));
            
            // 6. 计算横滚角（roll）- 通常在这种场景下保持为0
            float rollAngle = 0.0f;
            
            // 记录日志
            float finalYawAngle = yawAngle;
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, 
                    String.format("调整云台: 航向(Yaw)=%.1f°, 俯仰(Pitch)=%.1f°, 横滚(Roll)=%.1f°",
                            finalYawAngle, pitchAngle, rollAngle));
            });
            
            // 7. 应用计算出的角度到云台
            gimbalControl.rotateGimbalAbsolute(pitchAngle, yawAngle, rollAngle);
            
        } catch (Exception e) {
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "云台调整出错: " + e.getMessage());
            });
        }
    }

    /**
     * 获取航点控制权
     * @param instance
     * @return
     */
    public WaypointV2MissionOperator getWaypointMissionOperator(WaypointV2MissionOperator instance) {
        if (instance == null) {
            MissionControl missionControl = DJISDKManager.getInstance().getMissionControl();
            if (missionControl != null) {
                instance = missionControl.getWaypointMissionV2Operator();
            }
        }
        return instance;
    }


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

        Resources res = callback.mgetResources();
        Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.search_frame1);
        File imageFile = saveBitmapAsFile(bitmap,"frame1.jpg");

//        Bitmap bitmap = mfpvTexture.getBitmap();
//        if (bitmap == null) {
//            callback.addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 未准备好");
//            return null;
//        }
//
//        File imageFile = saveBitmapAsFile(bitmap, IMAGE_FILE_NAME);
//        if (imageFile == null) {
//            callback.addChatMessage(Constant.OWNER_BOT, "图片保存失败");
//            return null;
//        }
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


}
