package com.dji.sdk.voice_control.internal.controller.fragment.actuator;

import dji.common.mission.waypointv2.Action.WaypointActuator;

public interface IActuatorCallback {
    WaypointActuator getActuator();
}
