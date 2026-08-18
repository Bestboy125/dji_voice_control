# Embodied Agent UAV

An embodied-agent UAV system for interaction tasks

An Android ground station built on DJI Mobile SDK (MSDK V4). The drone is treated as a physical body for embodied intelligence: the camera is the “eye,” the propellers are the “legs.” The phone app handles motion control via MSDK (the cerebellum); cloud LLMs / VLMs and on-device vision foundation models handle cognition and decision-making (the brain). Together they close the **perceive — decide — control** loop.

<p align="center">
  <img src="fig/closeto.gif" width="46%"/>
  &nbsp;
  <img src="fig/track.gif" width="46%"/>
</p>
<p align="center">
  <em>Left: target approach and information collection &nbsp;|&nbsp; Right: voice-command confirmation and execution</em>
</p>

## Overview

Users issue high-level tasks in natural language (voice or text), e.g. “Collect information about the red car.” An LLM decomposes the task into **target search → target approach → target information collection**. A VLM then fuses the live camera feed, on-device YOLO scene descriptions, and flight-controller state, and uses chain-of-thought (CoT) to pick a low-level action (translate, rotate, gimbal, orbit, etc.). The action is sent to a Phantom 4-series aircraft through MSDK.

This repository corresponds to the system design, algorithms, software, and experiments in the undergraduate thesis *Design of an Embodied Agent UAV System for Embodied Interaction Tasks*.

## System Architecture

The agent has three parts: hardware, algorithms, and software. The phone app relays among the aircraft, the user, and cloud models: USB to the remote controller (MSDK), HTTP to the LLM / VLM.

<p align="center">
  <img src="fig/总体架构图.png" width="92%"/>
</p>
<p align="center"><em>Overall architecture: flight-control stack, agent decision pipeline, and application UI</em></p>

<p align="center">
  <img src="fig/具身实体设计.png" width="72%"/>
</p>
<p align="center"><em>Embodied body: camera for perception, propellers for locomotion, payload for actuation; phone MSDK flight control + cloud agent decisions</em></p>

Two interaction modes:

- **Human–UAV**: voice / text commands, with manual takeover in emergencies.
- **Self-interaction**: the UAV loops through observe — reason — act with the environment and keeps a replayable task trajectory.

## Core Capabilities

### Motion Control (Low-Level Action Library)

MSDK only exposes four parameters: `pitch / roll / yaw / throttle`. This project wraps them as callable actions in the BODY frame with GPS closed-loop position control (distance threshold ≈ 0.3 m):

| Action | Description | Implementation |
| --- | --- | --- |
| Translate 1/2/3 m (6 directions) | Up, down, left, right, forward, back | GPS closed loop + virtual stick |
| Rotate 45/90/180° | Yaw left / right | Heading closed loop |
| Gimbal down 30/60/90° | Change downward view | `gimbal.rotate()` |
| Orbit, radius 5–8 m | Collect information around (lat, lon) | Circular waypoints + waypoint mission |
| Takeoff / land | Leave the ground / land | MSDK |
| Fly to waypoint (lon, lat) | Heading alignment then straight-line flight | GPS closed loop |
| Switch subtask / hover | Task scheduling and hold | Control algorithm |

High-level skills are composed from these low-level actions:

| High-level skill | Composed of |
| --- | --- |
| Target search | Takeoff, translate, rotate, gimbal |
| Static-target approach | Translate, rotate |
| Target information collection | Orbit, waypoint flight |

### Perception, Memory, and Decision-Making

- **On-device perception**: YOLOv10 is converted PT → ONNX → NCNN and run on Android via C++ / JNI, emitting per-frame JSON (class, box, relative-position description).
- **Sliding-window memory**: the last 5 frames of scene descriptions and action sequences are kept as context for the next reasoning step, reducing hallucination.
- **CoT prompts**: the current subtask switches among four modes — task decomposition, spatial reasoning, action reasoning, and self-reflection (when the target is lost, analyze occlusion or FOV and recover).

<p align="center">
  <img src="fig/具身交互任务执行流程图.png" width="92%"/>
</p>
<p align="center"><em>Task execution: the LLM decomposes subtasks; the VLM fuses the frame / YOLO / flight state and outputs an action</em></p>

Typical step: YOLO describes the current frame → assemble history and UAV state → VLM outputs reasoning and an action (e.g. “move forward 2 m”) → the action is mapped to MSDK → memory is updated, until the subtask finishes and the next one starts.

## Software Modules

The Android app (`voice_control/`) is built on DJI MSDK 4.18:

| Module | Role |
| --- | --- |
| Speech recognition | iFLYTEK real-time ASR; press-and-hold to speak → confirm text → execute |
| Map and waypoints | Amap SDK: live position, tap-to-add waypoints, geocoding |
| Flight control and status | MSDK registration, virtual stick, waypoint / hotpoint missions, FPV, 10 Hz telemetry |
| Embodied interaction | Chat UI, task decomposition, VLM reasoning visualization, confirm-then-execute |
| Debug and logging | On-device debug panel; save task images and reasoning as image / TXT / HTML / PDF |

## Experiments

Hardware: DJI Phantom 4 series + remote controller + Android phone (USB). You can start with DJI Assistant 2 for Phantom in simulation, then fly outdoors in an open area with a clean magnetic environment.

Validated:

1. **Motion control**: takeoff and hover at ~5 m, circular orbit, map-selected waypoint flown in a straight line.
2. **Static-target approach**: lock a bus / car, then iterate translations from on-screen position and scale; if the target leaves the FOV, self-reflection pitches the gimbal down 45° to reacquire, until it is centered and the UAV hovers.
3. **Embodied interaction task**: “Collect information about the red car” is decomposed into search → approach → orbit collection, completing the observe—reason—act loop.

The GIFs above show target approach and confirmation of the voice command “turn left 90°.”

## Quick Start

### Requirements

- Android Studio (AGP 7.1.2)
- JDK 8, `minSdk 24`, `compileSdk 34`, ABI: `armeabi-v7a` / `arm64-v8a`
- DJI developer account and [MSDK App Key](https://developer.dji.com/)
- Amap key, iFLYTEK AppID, and an API key for GPT-4o (or a compatible endpoint)
- Aircraft: Phantom 4 series; or DJI Assistant 2 simulator on PC

### Setup

1. Clone this repo and open `voice_control/` in Android Studio.
2. Put your own DJI `com.dji.sdk.API_KEY` and Amap `com.amap.api.v2.apikey` in `voice_control/app/src/main/AndroidManifest.xml` (do not use sample values in the repo).
3. Configure the LLM and iFLYTEK keys in the app settings or the corresponding constants.
4. The first launch needs the internet for MSDK registration. After that, connect the remote controller over USB; if the UI shows aircraft status, the link is up.

### Flight

Before outdoor takeoff, make sure diagnostics are empty (compass / magnetic issues require recalibration or a different site). After a voice or text command, a confirmation dialog appears; only “confirm execution” sends the action to the flight controller. In an emergency, take over with the remote controller immediately.

> **Safety**: This project sends real flight-control commands. Fly only in legal airspace, follow local regulations, and keep visual line of sight and the ability to take over manually.

## Repository Layout

```
.
├── fig/                          # Architecture figures and experiment GIFs
├── voice_control/                # Android project
│   └── app/src/main/java/.../internal/
│       ├── controller/           # Link, flight control, speech, chat UI
│       ├── prompt/               # Task-decomposition / VLM prompts
│       └── ...
├── docs/                         # DJI MSDK API docs
└── README.md
```

Main implementations:

- Low-level flight control: `internal/controller/flightcontrol/`
- Embodied task agent: `internal/controller/flightcontrol/agent/`
- Prompts: `internal/prompt/`
- On-device YOLO: `internal/controller/yolo/`

## Citation

If this project helps your research, please cite:

```
Wang Mingtao. Design of an Embodied Agent UAV System for Embodied Interaction Tasks[D]. Shenyang: Northeastern University, 2025.
```

```bibtex
@thesis{wang2025embodied,
  title     = {Design of an Embodied Agent UAV System for Embodied Interaction Tasks},
  author    = {Wang, Mingtao},
  school    = {Northeastern University},
  year      = {2025},
  type      = {Bachelor's thesis}
}
```

## License

- This repo is derived from the DJI Android SDK Sample. The DJI Mobile SDK is subject to the [DJI EULA](http://developer.dji.com/policies/eula/).
- Sample code is under the MIT License; see `LICENSE.txt`.
- Apply for and keep your own third-party API keys. Do not commit secrets to a public repository.
