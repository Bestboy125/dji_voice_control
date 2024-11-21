package com.dji.sdk.voice_control.internal.controller.fragment.trigger;

import dji.common.mission.waypointv2.Action.WaypointTrigger;

public interface ITriggerCallback {
    WaypointTrigger getTrigger();
}
