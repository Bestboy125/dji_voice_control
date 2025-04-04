package com.dji.sdk.voice_control.internal.prompt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * TaskDecompositionPromptBuilder - 用于构建向LLM发送任务分解请求的prompt
 * 提供添加高层次能力、低层次能力和设置任务名称的接口
 */
public class TaskDecompositionPromptBuilder {
    private String taskName;
    private List<String> highLevelCapabilities = new ArrayList<>();
    private List<String> lowLevelActions = new ArrayList<>();
    private String outputFormat;
    private String instructionTemplate;
    
    /**
     * 构造函数
     */
    public TaskDecompositionPromptBuilder() {
        // 设置默认的输出格式要求
        this.outputFormat = "{\n" +
                "  \"任务名称\": \"任务名称\",\n" +
                "  \"任务流程\": [\n" +
                "    {\n" +
                "      \"步骤\": 1,\n" +
                "      \"子任务\": \"高层次能力中的一个\",\n" +
                "      \"描述\": \"描述步骤内容\",\n" +
                "      \"动作序列\": [\"动作1\", \"动作2\", ...]\n" +
                "    },\n" +
                "    ...\n" +
                "  ]\n" +
                "}";
        
        // 设置默认的指令模板
        this.instructionTemplate = "请针对\"%s\"的任务，结合上述高层次能力和低层次能力，对任务进行详细分解，子任务为高层次任务中进行选择。" +
                "步骤需要包含每个阶段的描述以及对应的具体动作序列。\n" +
                "要求返回结果格式为JSON格式的任务流程，如下所示：";
    }
    
    /**
     * 设置任务名称
     * @param taskName 任务名称
     * @return 当前构建器实例，支持链式调用
     */
    public TaskDecompositionPromptBuilder setTaskName(String taskName) {
        this.taskName = taskName;
        return this;
    }
    
    /**
     * 添加高层次能力
     * @param capability 高层次能力
     * @return 当前构建器实例，支持链式调用
     */
    public TaskDecompositionPromptBuilder addHighLevelCapability(String capability) {
        highLevelCapabilities.add(capability);
        return this;
    }
    
    /**
     * 批量添加高层次能力
     * @param capabilities 高层次能力列表
     * @return 当前构建器实例，支持链式调用
     */
    public TaskDecompositionPromptBuilder addHighLevelCapabilities(List<String> capabilities) {
        highLevelCapabilities.addAll(capabilities);
        return this;
    }
    
    /**
     * 添加低层次能力（动作）
     * @param action 低层次动作
     * @return 当前构建器实例，支持链式调用
     */
    public TaskDecompositionPromptBuilder addLowLevelAction(String action) {
        lowLevelActions.add(action);
        return this;
    }
    
    /**
     * 批量添加低层次能力（动作）
     * @param actions 低层次动作列表
     * @return 当前构建器实例，支持链式调用
     */
    public TaskDecompositionPromptBuilder addLowLevelActions(List<String> actions) {
        lowLevelActions.addAll(actions);
        return this;
    }
    
    /**
     * 设置自定义的指令模板
     * @param template 指令模板，必须包含%s作为任务名称的占位符
     * @return 当前构建器实例，支持链式调用
     */
    public TaskDecompositionPromptBuilder setInstructionTemplate(String template) {
        this.instructionTemplate = template;
        return this;
    }
    
    /**
     * 设置自定义的输出格式
     * @param format 期望的输出格式
     * @return 当前构建器实例，支持链式调用
     */
    public TaskDecompositionPromptBuilder setOutputFormat(String format) {
        this.outputFormat = format;
        return this;
    }
    
    /**
     * 构建完整的任务分解prompt
     * @return 完整的prompt字符串
     */
    public String build() {
        if (taskName == null || taskName.isEmpty()) {
            throw new IllegalStateException("任务名称不能为空");
        }
        
        if (highLevelCapabilities.isEmpty()) {
            throw new IllegalStateException("高层次能力列表不能为空");
        }
        
        if (lowLevelActions.isEmpty()) {
            throw new IllegalStateException("低层次能力列表不能为空");
        }
        
        StringBuilder promptBuilder = new StringBuilder();
        
        // 添加高层次能力部分
        promptBuilder.append("1. 高层次能力：\n");
        for (String capability : highLevelCapabilities) {
            promptBuilder.append("   - ").append(capability).append("\n");
        }
        promptBuilder.append("\n");
        
        // 添加低层次能力部分
        promptBuilder.append("2. 低层次能力（可选动作库）：\n");
        for (String action : lowLevelActions) {
            promptBuilder.append("   - \"").append(action).append("\"\n");
        }
        promptBuilder.append("\n");
        
        // 添加指令部分
        promptBuilder.append(String.format(instructionTemplate, taskName)).append("\n");
        
        // 添加输出格式要求
        promptBuilder.append(outputFormat);
        
        return promptBuilder.toString();
    }
    
    /**
     * 构建JSON格式的prompt（用于与其他prompt系统集成）
     * @return JSON格式的prompt
     * @throws JSONException JSON异常
     */
    public JSONObject buildAsJson() throws JSONException {
        JSONObject prompt = new JSONObject();
        
        // 添加任务名称
        prompt.put("任务名称", taskName);
        
        // 添加高层次能力
        JSONArray highLevelArray = new JSONArray();
        for (String capability : highLevelCapabilities) {
            highLevelArray.put(capability);
        }
        prompt.put("高层次能力", highLevelArray);
        
        // 添加低层次能力
        JSONArray lowLevelArray = new JSONArray();
        for (String action : lowLevelActions) {
            lowLevelArray.put(action);
        }
        prompt.put("低层次能力", lowLevelArray);
        
        // 添加指令和输出格式
        prompt.put("指令", String.format(instructionTemplate, taskName));
        prompt.put("期望输出格式", outputFormat);
        
        return prompt;
    }
    
    /**
     * 使用示例：构建针对"帮我收集目标信息"的任务分解prompt
     * @return 构建好的prompt
     */
    public static String buildCollectInfoTaskPrompt() {
        TaskDecompositionPromptBuilder builder = new TaskDecompositionPromptBuilder();
        
        // 设置任务名称
        builder.setTaskName("帮我收集目标信息");
        
        // 添加高层次能力
        List<String> highLevelCapabilities = new ArrayList<>();
        highLevelCapabilities.add("目标靠近正上方");
        highLevelCapabilities.add("目标搜索");
        highLevelCapabilities.add("目标定点绕飞");
        highLevelCapabilities.add("返航");
        builder.addHighLevelCapabilities(highLevelCapabilities);
        
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
        builder.addLowLevelActions(lowLevelActions);
        
        // 构建完整prompt
        return builder.build();
    }
    
    /**
     * 主方法示例：演示如何使用TaskDecompositionPromptBuilder
     */
    public static void main(String[] args) {
        String prompt = buildCollectInfoTaskPrompt();
        System.out.println(prompt);
    }
}