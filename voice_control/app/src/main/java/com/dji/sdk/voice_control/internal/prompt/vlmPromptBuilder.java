package com.dji.sdk.voice_control.internal.prompt;

import android.graphics.Bitmap;
import android.content.Context;
import com.dji.sdk.voice_control.internal.controller.yolo.SceneDescriptionGenerator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/**
 * PromptBuilder - 用于构建发送给大语言模型(VLM)的prompt
 * 提供添加场景描述、无人机位置和其他必要信息的接口
 */
public class vlmPromptBuilder {
    private static final int MAX_HISTORY_FRAMES = 10; // 保存历史帧的最大数量
    
    private JSONObject basePrompt;
    private JSONArray sceneDescriptions;
    private JSONArray dronePositions;
    private JSONArray actionHistory;
    private SceneDescriptionGenerator sceneGenerator;
    private int currentFrameNumber = 0;
    
    /**
     * 构造函数，初始化基本prompt结构
     * @param context 应用上下文
     * @param modelPath YOLO模型路径
     * @param labelPath 标签文件路径
     * @param taskObjective 当前子任务目标
     */
    public vlmPromptBuilder(Context context, String modelPath, String labelPath, String taskObjective) {
        try {
            // 初始化场景描述生成器
            sceneGenerator = new SceneDescriptionGenerator(context, modelPath, labelPath);
            
            // 初始化基本prompt结构
            initBasePrompt(taskObjective);
            
            // 初始化数组
            sceneDescriptions = new JSONArray();
            dronePositions = new JSONArray();
            actionHistory = new JSONArray();
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化基本prompt结构
     * @param taskObjective 当前子任务目标
     * @throws JSONException JSON异常
     */
    private void initBasePrompt(String taskObjective) throws JSONException {
        basePrompt = new JSONObject();
        
        // 添加任务背景
        basePrompt.put("任务背景", "当前系统正在执行一个智能体任务，目标是按照用户指令进行信息收集。系统已将任务划分为多个子任务，并在当前阶段执行具体的子任务。请根据输入的信息，结合环境感知与历史执行记录，推理并输出一个最适合的动作。");
        
        // 添加任务信息，包含初始空的子任务列表
        JSONObject taskInfo = new JSONObject();
        taskInfo.put("当前子任务目标", taskObjective);
        
        // 初始化空的子任务列表
        JSONObject subtasksList = new JSONObject();
        taskInfo.put("子任务列表", subtasksList);
        
        basePrompt.put("任务信息", taskInfo);
        
        // 添加视觉信息结构
        JSONObject visualInfo = new JSONObject();
        basePrompt.put("视觉信息", visualInfo);
        
        // 添加可选动作库，增加切换任务目标的选项和悬停选项
        JSONArray actionLibrary = new JSONArray();
        actionLibrary.put("无人机向（上/下/左/右/前/后）移动（1/2/3）米");
        actionLibrary.put("无人机向（左/右）旋转（45/90/180）度");
        actionLibrary.put("云台向下倾斜（30/60/90）度");
        actionLibrary.put("以（5/6/7/8）米的半径以（lat、lon）为圆心定点绕飞");
        actionLibrary.put("起飞");
        actionLibrary.put("降落");
        actionLibrary.put("指定航点（lon,lat）执行");
        actionLibrary.put("切换当前任务目标为(子任务列表中的一个)");
        actionLibrary.put("悬停");
        basePrompt.put("可选动作库", actionLibrary);
        
        // 添加任务要求
        JSONArray taskRequirements = new JSONArray();
        taskRequirements.put("请基于当前图像、历史动作及无人机状态，推理一个最佳的动作");
        taskRequirements.put("输出格式：以json格式输出动作库中的动作，并给出你的推理过程");
        basePrompt.put("任务要求", taskRequirements);
        
        // 应用当前任务相关的思维链
        applyThinkingChainByCurrentTask();
    }
    
    /**
     * 初始化子任务列表
     * @param subtasks 子任务列表数组
     */
    public void initializeSubtaskList(String[] subtasks) {
        try {
            JSONObject taskInfo = basePrompt.getJSONObject("任务信息");
            JSONObject subtasksList = new JSONObject();
            
            for (int i = 0; i < subtasks.length; i++) {
                subtasksList.put("任务序号" + (i + 1), subtasks[i]);
            }
            
            taskInfo.put("子任务列表", subtasksList);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 根据当前子任务自动选择并应用相应的思维链
     * 该方法会移除之前的所有思维链，并只保留当前任务相关的思维链
     */
    public void applyThinkingChainByCurrentTask() {
        try {
            // 获取当前子任务目标
            JSONObject taskInfo = basePrompt.getJSONObject("任务信息");
            String currentTask = taskInfo.getString("当前子任务目标");
            
            // 移除所有现有的思维链键（通过查找包含"思维链"的键）
            for (Iterator<String> it = basePrompt.keys(); it.hasNext();) {
                String key = it.next();
                if (key.contains("思维链")) {
                    it.remove();
                }
            }
            
            // 根据当前任务添加对应的思维链
            if (currentTask.equals("目标搜索")) {
                basePrompt.put("目标搜索思维链", getTargetSearchThinkingChain());
            } else if (currentTask.equals("靠近目标正上方")) {
                basePrompt.put("靠近目标思维链", getApproachTargetThinkingChain());
            } else if (currentTask.equals("目标信息收集")) {
                basePrompt.put("目标信息收集思维链", getInfoCollectionThinkingChain());
            } else {
                // 默认使用靠近目标思维链
                basePrompt.put("靠近目标思维链", getApproachTargetThinkingChain());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 更改当前子任务目标并自动切换相应的思维链
     * @param newTaskObjective 新的子任务目标
     */
    public void changeCurrentTaskObjectiveWithThinking(String newTaskObjective) {
        try {
            // 更改任务目标
            JSONObject taskInfo = basePrompt.getJSONObject("任务信息");
            taskInfo.put("当前子任务目标", newTaskObjective);
            
            // 自动应用对应思维链
            applyThinkingChainByCurrentTask();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 根据任务序号更改当前子任务目标并自动切换相应的思维链
     * @param taskNumber 任务序号（从1开始）
     * @return 是否成功更改任务目标
     */
    public boolean changeTaskObjectiveByNumberWithThinking(int taskNumber) {
        try {
            JSONObject taskInfo = basePrompt.getJSONObject("任务信息");
            JSONObject subtasksList = taskInfo.getJSONObject("子任务列表");
            
            String taskKey = "任务序号" + taskNumber;
            if (subtasksList.has(taskKey)) {
                String newObjective = subtasksList.getString(taskKey);
                taskInfo.put("当前子任务目标", newObjective);
                
                // 自动应用对应思维链
                applyThinkingChainByCurrentTask();
                return true;
            }
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取目标搜索思维链内容
     */
    private String getTargetSearchThinkingChain() {
        return "你是一架智能无人机，需要根据以下信息和思维链决策最优移动方式：\n\n" +
               "1. **环境信息收集**：获取当前无人机位置、传感器数据以及历史帧目标位置信息。\n" +
               "2. **初步目标检测**：检查当前视野内是否存在目标（如红色车辆）；若检测到则切换至靠近任务，否则继续搜索。\n" +
               "3. **扩大搜索视野**：如果目标不在当前视野内，调整云台（如向下倾斜30度）以覆盖更大范围。\n" +
               "4. **规划搜索路径**：基于历史数据和目标可能出现区域，制定搜索策略（例如横向或纵向覆盖搜索、蛇形路径或螺旋搜索）。\n" +
               "5. **选择最优行动指令**：从动作库中挑选最合适的指令（如左右旋转45度、无人机向前移动X米、无人机向左移动Y米）以执行搜索。\n" +
               "6. 根据后一个子任务目标，结合当前场景描述，判断是否应该执行下一次子任务了，如果所有的任务已经执行完毕，那么给出悬停的动作。\n" +
               "7. **实时反馈与调整**：执行后重新扫描目标信息，根据新数据不断更新搜索策略，直到锁定目标。";
    }
    
    /**
     * 获取靠近目标思维链内容
     */
    private String getApproachTargetThinkingChain() {
        return "你是一架智能无人机，需要根据以下信息和思维链决策最优移动方式：\n\n" +
               "1. **分析目标的相对位置趋势**：基于历史帧数据，判断目标（如车）的运动方向。\n" +
               "2. **判断目标是否仍在视野范围内**：若目标离开视野，调整云台角度或者无人机使目标入镜。\n" +
               "3. **执行调整**：若目标不在视野内，选择合适的移动方向（如向前、向后、左、右等），使目标重新进入视野,或者调整云台向下倾斜30度。\n" +
               "4. **基于趋势优化移动方式**：如果目标在视野内，根据目标的运动趋势决定无人机的下一步移动（向前/向后/左/右/上/下）。\n" +
               "5. 根据后一个子任务目标，结合当前场景描述，判断是否应该执行下一次子任务了，如果所有的任务已经执行完毕，那么给出悬停的动作。\n" +
               "6. **输出JSON格式的行动指令**：请仅输出动作库中无人机的最优行动指令（JSON格式），不包含额外解释。";
    }
    
    /**
     * 获取目标信息收集思维链内容
     */
    private String getInfoCollectionThinkingChain() {
        return "你是一架智能无人机，需要根据以下信息和思维链决策最优移动方式：\n\n" +
               "1. **确认当前位置与目标关系**  - 验证无人机是否确实位于目标正上方，确保目标在视野中央且相对位置稳定。\n" +
               "2. **评估目标状态**  - 检查目标是否有运动趋势或其他异常情况，如目标是否开始移动、目标特征是否发生变化。\n" +
               "3. **判断是否可以进行目标信息收集了** ：必要时应当进行悬停进行观察。\n" +
               "4. **从动作库中选取最优指令**  - 根据上方分析，从动作库中挑选出唯一符合当前需求的动作，例如：\n" +
               "     - 若需进一步下降观察目标：选择无人机向下移动（1/2/3）米。\n" +
               "     - 若需要改变任务目标：选择切换当前任务目标为(子任务列表中的一个)。\n" +
               "5. **输出JSON格式的行动指令**：请仅输出动作库中无人机的最优行动指令（JSON格式），不包含额外解释。";
    }
    
    /**
     * 保存自定义思维链内容
     * @param thinkingChainType 思维链类型 (如 "目标搜索思维链", "靠近目标思维链", "目标信息收集思维链")
     * @param content 自定义思维链内容
     */
    public void setCustomThinkingChain(String thinkingChainType, String content) {
        try {
            basePrompt.put(thinkingChainType, content);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 初始化所有思维链内容
     * 这个方法会将所有预定义的思维链存储起来，但只应用当前任务需要的思维链
     */
    public void initializeAllThinkingChains() {
        // 先存储所有预定义的思维链
        Map<String, String> allThinkingChains = new HashMap<>();
        allThinkingChains.put("目标搜索思维链", getTargetSearchThinkingChain());
        allThinkingChains.put("靠近目标思维链", getApproachTargetThinkingChain());
        allThinkingChains.put("目标信息收集思维链", getInfoCollectionThinkingChain());
        
        // 只应用当前任务相关的思维链
        applyThinkingChainByCurrentTask();
    }
    
    /**
     * 添加当前帧的场景描述
     * @param image 当前帧图像
     * @return 返回生成的场景描述
     */
    public JSONObject addCurrentSceneDescription(Bitmap image) {
        currentFrameNumber++;
        JSONObject sceneDesc = sceneGenerator.generateDescription(image, currentFrameNumber);
        
        try {
            // 将当前场景描述添加到历史列表
            sceneDescriptions.put(sceneDesc);
            
            // 保持历史长度在限制范围内
            if (sceneDescriptions.length() > MAX_HISTORY_FRAMES) {
                JSONArray newArray = new JSONArray();
                for (int i = sceneDescriptions.length() - MAX_HISTORY_FRAMES; i < sceneDescriptions.length(); i++) {
                    newArray.put(sceneDescriptions.get(i));
                }
                sceneDescriptions = newArray;
            }
            
            // 更新视觉信息中的历史场景描述
            JSONObject visualInfo = basePrompt.getJSONObject("视觉信息");
            visualInfo.put("当前图像描述", sceneDesc);
            visualInfo.put("历史场景描述", sceneDescriptions);
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return sceneDesc;
    }
    
    /**
     * 添加无人机位置信息
     * @param longitude 经度
     * @param latitude 纬度
     */
    public void addDronePosition(double longitude, double latitude) {
        try {
            JSONObject posInfo = new JSONObject();
            posInfo.put("帧序号", currentFrameNumber);
            posInfo.put("经度", longitude);
            posInfo.put("纬度", latitude);
            
            // 添加到历史位置列表
            dronePositions.put(posInfo);
            
            // 保持历史长度在限制范围内
            if (dronePositions.length() > MAX_HISTORY_FRAMES) {
                JSONArray newArray = new JSONArray();
                for (int i = dronePositions.length() - MAX_HISTORY_FRAMES; i < dronePositions.length(); i++) {
                    newArray.put(dronePositions.get(i));
                }
                dronePositions = newArray;
            }
            
            // 更新视觉信息中的历史无人机位置
            JSONObject visualInfo = basePrompt.getJSONObject("视觉信息");
            visualInfo.put("历史无人机位置信息", dronePositions);
            
            // 更新无人机状态信息中的位置
            JSONObject droneState = basePrompt.optJSONObject("无人机状态信息");
            if (droneState == null) {
                droneState = new JSONObject();
                basePrompt.put("无人机状态信息", droneState);
            }
            
            JSONObject positionInfo = droneState.optJSONObject("位置信息");
            if (positionInfo == null) {
                positionInfo = new JSONObject();
                droneState.put("位置信息", positionInfo);
            }
            
            positionInfo.put("Lat", latitude);
            positionInfo.put("Lon", longitude);
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 添加无人机执行的动作
     * @param action 执行的动作描述
     */
    public void addExecutedAction(String action) {
        try {
            // 添加到动作历史
            actionHistory.put(action);
            
            // 保持历史长度在限制范围内
            if (actionHistory.length() > MAX_HISTORY_FRAMES) {
                JSONArray newArray = new JSONArray();
                for (int i = actionHistory.length() - MAX_HISTORY_FRAMES; i < actionHistory.length(); i++) {
                    newArray.put(actionHistory.get(i));
                }
                actionHistory = newArray;
            }
            
            // 更新视觉信息中的历史执行动作序列
            JSONObject visualInfo = basePrompt.getJSONObject("视觉信息");
            visualInfo.put("历史执行的动作序列", actionHistory);
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 更新无人机状态信息
     * @param speed 速度信息 (X, Y, Z)
     * @param attitude 姿态角 (Yaw, Roll, Pitch)
     * @param isFlying 是否正在飞行
     * @param gpsSignalLevel GPS信号等级
     * @param windLevel 环境大风等级
     */
    public void updateDroneState(double[] speed, double[] attitude, 
                                 boolean isFlying, int gpsSignalLevel, int windLevel) {
        try {
            JSONObject droneState = basePrompt.optJSONObject("无人机状态信息");
            if (droneState == null) {
                droneState = new JSONObject();
                basePrompt.put("无人机状态信息", droneState);
            }
            
            // 速度信息
            JSONObject speedInfo = new JSONObject();
            speedInfo.put("X", speed[0]);
            speedInfo.put("Y", speed[1]);
            speedInfo.put("Z", speed[2]);
            droneState.put("速度信息", speedInfo);
            
            // 姿态角
            JSONObject attitudeInfo = new JSONObject();
            attitudeInfo.put("Yaw", attitude[0]);
            attitudeInfo.put("Roll", attitude[1]);
            attitudeInfo.put("Pitch", attitude[2]);
            droneState.put("姿态角", attitudeInfo);
            
            // 其他状态
            droneState.put("是否正在飞行", isFlying);
            droneState.put("GPS信号等级", gpsSignalLevel);
            droneState.put("环境大风等级", windLevel);
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 获取完整的prompt
     * @return 完整的prompt JSON对象
     */
    public JSONObject getFullPrompt() {
        return basePrompt;
    }
    
    /**
     * 获取完整的prompt字符串
     * @return 完整的prompt JSON字符串
     */
    public String getFullPromptString() {
        return basePrompt.toString();
    }
    
    /**
     * 关闭和释放资源
     */
    public void close() {
        if (sceneGenerator != null) {
            sceneGenerator.close();
        }
    }
    
    /**
     * 使用示例 - 更新版本包含思维链自动切换
     */
    public static void usageExample(Context context) {
        // 创建PromptBuilder实例
        vlmPromptBuilder builder = new vlmPromptBuilder(
                context,
                "models/yolov5s.tflite", 
                "models/coco.txt",
                "目标搜索");  // 初始任务为目标搜索
        
        try {
            // 初始化子任务列表
            String[] subtasks = {"目标搜索", "靠近目标正上方", "目标信息收集"};
            builder.initializeSubtaskList(subtasks);
            
            // 初始化所有思维链
            builder.initializeAllThinkingChains();
            
            // 模拟获取图像帧
            Bitmap frame = null; // 假设这里获取到了图像
            
            // 添加场景描述
            builder.addCurrentSceneDescription(frame);
            
            // 添加无人机位置
            builder.addDronePosition(121.4737, 31.2304);
            
            // 添加已执行的动作
            builder.addExecutedAction("无人机向前移动2米");
            
            // 更新无人机状态
            double[] speed = {0.0, 0.0, 0.0};
            double[] attitude = {0.0, 1.0, 2.0};
            builder.updateDroneState(speed, attitude, true, 5, 2);
            
            // 假设检测到目标，切换任务目标并自动应用相应思维链
            builder.changeCurrentTaskObjectiveWithThinking("靠近目标正上方");
            
            // 或通过任务序号切换
            builder.changeTaskObjectiveByNumberWithThinking(2); // 切换到"靠近目标正上方"
            
            // 模拟靠近目标后切换到信息收集
            builder.changeCurrentTaskObjectiveWithThinking("目标信息收集");
            
            // 获取完整的prompt
            String fullPrompt = builder.getFullPromptString();
            
            // 使用完毕后释放资源
            builder.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 