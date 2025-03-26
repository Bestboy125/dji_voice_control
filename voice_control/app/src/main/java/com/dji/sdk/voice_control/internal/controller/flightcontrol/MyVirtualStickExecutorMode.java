package com.dji.sdk.voice_control.internal.controller.flightcontrol;

/**
 * Virtual Stick Executor Mode
 */

public enum MyVirtualStickExecutorMode {
    UNINITIALIZED,
    UP_WITHOUT_DIS,
    UP_DIS,
    DOWN_WITHOUT_DIS,
    DOWN_DIS,
    MOVE_WITHOUT_DIS,
    MOVE_DIS,
    MOVE_DIS_SPEED,
    FLY_TO,
    TURN,
    STOP
}