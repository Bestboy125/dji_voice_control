package com.dji.sdk.voice_control.internal.controller.flightcontrol.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.util.Log;
import android.view.TextureView;

import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.interfaces.ControlActivityCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.internal.WhiteboxImpl;

/**
 * 单元测试：无人机位置调整功能
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({
        CommandInterpreter.class,
        MyVirtualStickExecutor.class, 
        llm_agent_cycle.class,
        Log.class
})
@PowerMockIgnore({
        "javax.management.*", 
        "javax.xml.*", 
        "org.xml.*", 
        "org.w3c.*", 
        "org.apache.logging.*",
        "android.util.Log"
})
public class LlmAgentCycleTest {

    private CommandInterpreter mockCommandInterpreter;
    private TextureView mockTextureView;
    private ControlActivityCallback mockCallback;
    private MyVirtualStickExecutor mockVirtualStickExecutor;
    
    private llm_agent_cycle agentCycle;
    private llm_agent_cycle spyAgentCycle;

    @Before
    public void setUp() throws Exception {
        // 模拟Android Log
        PowerMockito.mockStatic(Log.class);
        
        // 使用PowerMockito创建模拟对象
        mockCommandInterpreter = PowerMockito.mock(CommandInterpreter.class);
        mockTextureView = PowerMockito.mock(TextureView.class);
        mockCallback = PowerMockito.mock(ControlActivityCallback.class);
        mockVirtualStickExecutor = PowerMockito.mock(MyVirtualStickExecutor.class);
        
        // 模拟静态方法
        PowerMockito.mockStatic(MyVirtualStickExecutor.class);
        PowerMockito.when(MyVirtualStickExecutor.getUniqueInstance()).thenReturn(mockVirtualStickExecutor);
        
        // 初始化测试对象 - 使用只需要三个参数的构造函数
        agentCycle = new llm_agent_cycle(
                mockCommandInterpreter,
                mockTextureView,
                mockCallback
        );
        
        // 创建spy对象以便可以部分模拟方法
        spyAgentCycle = PowerMockito.spy(agentCycle);
        
        // 模拟SleepThread方法，避免实际等待
        PowerMockito.doNothing().when(spyAgentCycle, "SleepThread", anyInt());
        
        // 设置目标对象类型
        agentCycle.setTargetObjectType("黑色轿车");
    }

    @Test
    public void testCalculateHorizontalOffset() throws Exception {
        // 使用反射调用私有方法
        double leftOffset = WhiteboxImpl.invokeMethod(agentCycle, "calculateHorizontalOffset", "left");
        double rightOffset = WhiteboxImpl.invokeMethod(agentCycle, "calculateHorizontalOffset", "right");
        double centerOffset = WhiteboxImpl.invokeMethod(agentCycle, "calculateHorizontalOffset", "center");
        double unknownOffset = WhiteboxImpl.invokeMethod(agentCycle, "calculateHorizontalOffset", "unknown");

        // 验证结果
        assertEquals(-0.7, leftOffset, 0.001);
        assertEquals(0.7, rightOffset, 0.001);
        assertEquals(0.0, centerOffset, 0.001);
        assertEquals(0.0, unknownOffset, 0.001);
    }

    @Test
    public void testCalculateVerticalOffset() throws Exception {
        // 使用反射调用私有方法
        double backwardOffset = WhiteboxImpl.invokeMethod(agentCycle, "calculateVerticalOffset", "backward");
        double forwardOffset = WhiteboxImpl.invokeMethod(agentCycle, "calculateVerticalOffset", "forward");
        double centerOffset = WhiteboxImpl.invokeMethod(agentCycle, "calculateVerticalOffset", "center");
        double unknownOffset = WhiteboxImpl.invokeMethod(agentCycle, "calculateVerticalOffset", "unknown");

        // 验证结果
        assertEquals(-0.7, backwardOffset, 0.001);
        assertEquals(0.7, forwardOffset, 0.001);
        assertEquals(0.0, centerOffset, 0.001);
        assertEquals(0.0, unknownOffset, 0.001);
    }

    @Test
    public void testCalculateProximityFactor() throws Exception {
        // 使用反射调用私有方法
        double factor0 = WhiteboxImpl.invokeMethod(agentCycle, "calculateProximityFactor", 0);
        double factor50 = WhiteboxImpl.invokeMethod(agentCycle, "calculateProximityFactor", 50);
        double factor100 = WhiteboxImpl.invokeMethod(agentCycle, "calculateProximityFactor", 100);

        // 验证结果
        assertEquals(0.0, factor0, 0.001);
        assertEquals(Math.pow(0.5, 0.7), factor50, 0.001);
        assertEquals(1.0, factor100, 0.001);
    }

    @Test
    public void testCalculateHorizontalMoveDistance() throws Exception {
        // 使用反射调用私有方法
        double dist1 = WhiteboxImpl.invokeMethod(agentCycle, "calculateHorizontalMoveDistance", 0.7, 0.0);
        double dist2 = WhiteboxImpl.invokeMethod(agentCycle, "calculateHorizontalMoveDistance", 0.7, 0.5);
        double dist3 = WhiteboxImpl.invokeMethod(agentCycle, "calculateHorizontalMoveDistance", 0.7, 1.0);

        // 验证结果是否在预期范围内
        assertEquals(1.4, dist1, 0.001); // 0.7 * 2.0 = 1.4
        assertEquals(0.85, dist2, 0.05); // 约为 1.4 * (1 - 0.7 * 0.5) = 0.91
        assertEquals(0.5, dist3, 0.05); // 由于 MIN_MOVE_DISTANCE 应该是 0.5
    }

    @Test
    public void testCalculateForwardMoveDistance() throws Exception {
        // 使用反射调用私有方法
        double dist1 = WhiteboxImpl.invokeMethod(agentCycle, "calculateForwardMoveDistance", 0.0);
        double dist2 = WhiteboxImpl.invokeMethod(agentCycle, "calculateForwardMoveDistance", 0.5);
        double dist3 = WhiteboxImpl.invokeMethod(agentCycle, "calculateForwardMoveDistance", 1.0);

        // 验证结果
        assertTrue(dist1 <= 3.0 && dist1 > 0.5); // MAX_MOVE_DISTANCE 应该是 3.0
        assertTrue(dist2 < dist1 && dist2 > dist3);
        assertEquals(0.5, dist3, 0.05); // MIN_MOVE_DISTANCE 应该是 0.5
    }

    @Test
    public void testExecuteAdjustmentMovement_LeftPosition() throws Exception {
        // 设置回调和虚拟摇杆执行器
        PowerMockito.doNothing().when(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        PowerMockito.doNothing().when(mockVirtualStickExecutor).mGo(anyInt(), anyDouble());
        
        // 设置 mSingletonVirtualStickExecutor 字段
        WhiteboxImpl.setInternalState(spyAgentCycle, "mSingletonVirtualStickExecutor", mockVirtualStickExecutor);
        
        // 调用测试方法
        spyAgentCycle.executeAdjustmentMovement(-0.7, 1.0, 0.0, 0.0, 0.0, 0.5);
        
        // 验证结果
        verify(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        verify(mockVirtualStickExecutor).mGo(eq(303), anyDouble());
    }

    @Test
    public void testExecuteAdjustmentMovement_RightPosition() throws Exception {
        // 设置回调和虚拟摇杆执行器
        PowerMockito.doNothing().when(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        PowerMockito.doNothing().when(mockVirtualStickExecutor).mGo(anyInt(), anyDouble());
        
        // 设置 mSingletonVirtualStickExecutor 字段
        WhiteboxImpl.setInternalState(spyAgentCycle, "mSingletonVirtualStickExecutor", mockVirtualStickExecutor);
        
        // 调用测试方法
        spyAgentCycle.executeAdjustmentMovement(0.7, 1.0, 0.0, 0.0, 0.0, 0.5);
        
        // 验证结果
        verify(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        verify(mockVirtualStickExecutor).mGo(eq(304), anyDouble());
    }

    @Test
    public void testExecuteAdjustmentMovement_ForwardPosition() throws Exception {
        // 设置回调和虚拟摇杆执行器
        PowerMockito.doNothing().when(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        PowerMockito.doNothing().when(mockVirtualStickExecutor).mGo(anyInt(), anyDouble());
        
        // 设置 mSingletonVirtualStickExecutor 字段
        WhiteboxImpl.setInternalState(spyAgentCycle, "mSingletonVirtualStickExecutor", mockVirtualStickExecutor);
        
        // 调用测试方法
        spyAgentCycle.executeAdjustmentMovement(0.0, 0.0, 0.7, 1.0, 0.0, 0.5);
        
        // 验证结果
        verify(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        verify(mockVirtualStickExecutor).mGo(eq(301), anyDouble());
    }

    @Test
    public void testExecuteAdjustmentMovement_BackwardPosition() throws Exception {
        // 设置回调和虚拟摇杆执行器
        PowerMockito.doNothing().when(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        PowerMockito.doNothing().when(mockVirtualStickExecutor).mGo(anyInt(), anyDouble());
        
        // 设置 mSingletonVirtualStickExecutor 字段
        WhiteboxImpl.setInternalState(spyAgentCycle, "mSingletonVirtualStickExecutor", mockVirtualStickExecutor);
        
        // 调用测试方法
        spyAgentCycle.executeAdjustmentMovement(0.0, 0.0, -0.7, 1.0, 0.0, 0.5);
        
        // 验证结果
        verify(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        verify(mockVirtualStickExecutor).mGo(eq(302), anyDouble());
    }

    @Test
    public void testExecuteAdjustmentMovement_CenterFar() throws Exception {
        // 设置回调和虚拟摇杆执行器
        PowerMockito.doNothing().when(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        PowerMockito.doNothing().when(mockVirtualStickExecutor).mGo(anyInt(), anyDouble());
        
        // 设置 mSingletonVirtualStickExecutor 字段
        WhiteboxImpl.setInternalState(spyAgentCycle, "mSingletonVirtualStickExecutor", mockVirtualStickExecutor);
        
        // 调用测试方法 - 居中但较远
        spyAgentCycle.executeAdjustmentMovement(0.0, 0.0, 0.0, 0.0, 1.5, 0.4);
        
        // 验证结果
        verify(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        verify(mockVirtualStickExecutor).mGo(eq(301), eq(1.5));
    }

    @Test
    public void testExecuteAdjustmentMovement_CenterClose() throws Exception {
        // 设置回调和虚拟摇杆执行器
        PowerMockito.doNothing().when(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        PowerMockito.doNothing().when(mockVirtualStickExecutor).mGo(anyInt(), anyDouble());
        
        // 设置 mSingletonVirtualStickExecutor 字段
        WhiteboxImpl.setInternalState(spyAgentCycle, "mSingletonVirtualStickExecutor", mockVirtualStickExecutor);
        
        // 调用测试方法 - 居中且接近
        spyAgentCycle.executeAdjustmentMovement(0.0, 0.0, 0.0, 0.0, 0.5, 0.7);
        
        // 验证结果
        verify(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        verify(mockVirtualStickExecutor).mGo(eq(301), eq(0.5));
    }

    @Test
    public void testExecuteAdjustmentMovement_CenterVeryClose() throws Exception {
        // 设置回调和虚拟摇杆执行器
        PowerMockito.doNothing().when(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        PowerMockito.doNothing().when(mockVirtualStickExecutor).mGo(anyInt(), anyDouble());
        
        // 设置 mSingletonVirtualStickExecutor 字段
        WhiteboxImpl.setInternalState(spyAgentCycle, "mSingletonVirtualStickExecutor", mockVirtualStickExecutor);
        
        // 调用测试方法 - 居中且非常接近
        spyAgentCycle.executeAdjustmentMovement(0.0, 0.0, 0.0, 0.0, 0.0, 0.9);
        
        // 验证结果
        verify(mockCallback).addChatMessage(eq(Constant.OWNER_BOT), anyString());
        verify(mockVirtualStickExecutor, times(0)).mGo(anyInt(), anyDouble()); // 不应该移动
    }

    @Test
    public void testAdjustDronePosition() throws Exception {
        // 创建spyAgentCycle以便可以部分模拟方法
        PowerMockito.doReturn(-0.7).when(spyAgentCycle, "calculateHorizontalOffset", "left");
        PowerMockito.doReturn(0.0).when(spyAgentCycle, "calculateVerticalOffset", "left");
        PowerMockito.doReturn(0.5).when(spyAgentCycle, "calculateProximityFactor", 50);
        PowerMockito.doReturn(1.0).when(spyAgentCycle, "calculateHorizontalMoveDistance", anyDouble(), anyDouble());
        PowerMockito.doReturn(0.5).when(spyAgentCycle, "calculateForwardMoveDistance", anyDouble());
        
        // 设置回调和虚拟摇杆执行器
        PowerMockito.doNothing().when(spyAgentCycle).executeAdjustmentMovement(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        
        // 调用测试方法
        spyAgentCycle.adjustDronePosition("left", 50);
        
        // 验证方法调用
        PowerMockito.verifyPrivate(spyAgentCycle).invoke("calculateHorizontalOffset", "left");
        PowerMockito.verifyPrivate(spyAgentCycle).invoke("calculateVerticalOffset", "left");
        PowerMockito.verifyPrivate(spyAgentCycle).invoke("calculateProximityFactor", 50);
        PowerMockito.verifyPrivate(spyAgentCycle).invoke("calculateHorizontalMoveDistance", -0.7, 0.5);
        PowerMockito.verifyPrivate(spyAgentCycle).invoke("calculateHorizontalMoveDistance", 0.0, 0.5);
        PowerMockito.verifyPrivate(spyAgentCycle).invoke("calculateForwardMoveDistance", 0.5);
        
        // 使用ArgumentCaptor捕获参数
        ArgumentCaptor<Double> horizontalOffsetCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> horizontalMoveDistanceCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> verticalOffsetCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> verticalMoveDistanceCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> forwardMoveDistanceCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> proximityFactorCaptor = ArgumentCaptor.forClass(Double.class);
        
        PowerMockito.verifyPrivate(spyAgentCycle).invoke(
                "executeAdjustmentMovement",
                horizontalOffsetCaptor.capture(),
                horizontalMoveDistanceCaptor.capture(),
                verticalOffsetCaptor.capture(),
                verticalMoveDistanceCaptor.capture(),
                forwardMoveDistanceCaptor.capture(),
                proximityFactorCaptor.capture()
        );
        
        // 验证参数值
        assertEquals(-0.7, horizontalOffsetCaptor.getValue(), 0.001); // left = -0.7
        assertEquals(1.0, horizontalMoveDistanceCaptor.getValue(), 0.001); // 我们模拟了返回1.0
        assertEquals(0.0, verticalOffsetCaptor.getValue(), 0.001); // 我们模拟了返回0.0
        assertEquals(0.5, proximityFactorCaptor.getValue(), 0.001); // 我们模拟了返回0.5
    }
} 