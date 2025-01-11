package com.dji.sdk.voice_control.internal.controller.flightcontrol;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.controller.Utils;

import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Main flight control module
 */
public class MyVirtualStickExecutor {

    //region 控制数据结构
    //设置虚拟控制指令模式
    private MyVirtualStickExecutorMode mMode = MyVirtualStickExecutorMode.UNINITIALIZED;
    private Context mContext;
    //飞行控制数据
    private float mSpeed = 3;
    private float mPitch = 0;
    private float mRoll = 0;
    private float mYaw = 0;
    private float mThrottle = 0;

    //发送虚拟数据计时器和发送任务
    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;

    //位置跟踪计时器和位置跟踪任务
    private Timer mLocationTrackTimer;
    private LocationTrackTask mLocationTrackTask;

    //飞行控制
    private static FlightController mFlightController;

    /**
     * singleton pattern
     */
    private static MyVirtualStickExecutor uniqueInstance = null;
    //endregion

    //region 构造函数
    /**
     * always private, no use
     */
    private MyVirtualStickExecutor(Context context) {
        mContext = context;
    }
    private MyVirtualStickExecutor() {
    }
    //endregion

    //region 命令队列数据结构
    // Command queue
    private Queue<DroneCommand> commandQueue = new LinkedList<>();
    private boolean isExecuting = false;
    //endregion

    //region 飞行控制初始化
    /**
     * 初始化飞行控制
     */
    public static void initFlightController() {
        mFlightController = DJISampleApplication.getFlightController();
    }

    /**
     *获取特定的飞行控制类
     * @return virtual stick executor
     */
    public static MyVirtualStickExecutor getUniqueInstance() {
        if (uniqueInstance == null) {
            uniqueInstance = new MyVirtualStickExecutor();
        }
        initFlightController();
        MyChangeSettingsExecutor.mEnableVS();
        MyChangeSettingsExecutor.setConventionVirtualStickMode();
        uniqueInstance.initYaw();
//        uniqueInstance.initAltitude();
        uniqueInstance.checkSendVirtualStickDataTimer();
        return uniqueInstance;
    }

    /**
     * 初始化无人机的航向
     */
    private void initYaw() {
        mYaw = mFlightController.getCompass().getHeading();
    }

//    private void initAltitude (){hpAltitude=mFlightController.getState().getAircraftLocation().getAltitude();}
    //endregion

    //region 飞行数据获取
    /**
     * 获取当前的速度，以及三个角，马力
     */
    public void getFlight(FlightData flightData){
        flightData.setSpeed(mSpeed);
        flightData.setPitch(mPitch);
        flightData.setRoll(mRoll);
        flightData.setYaw(mYaw);
        flightData.setThrottle(mThrottle);
    }

    /**
     * 获取当前的高度
     */
    private double getCurrentAltitude() {
        double alti = (double) mFlightController.getState().getAircraftLocation().getAltitude(); //-hpAltitude;
//        if (alti<18){
//            alti = (double) mFlightController.getState().getUltrasonicHeightInMeters();
//        }
        return alti;
    }

    /**
     * 改变速度
     */
    protected void setSpeed(int s) {
        mSpeed = (float) s;
        if (mPitch != 0) {
            if (mPitch > 0) {
                mPitch = mSpeed;
            } else {
                mPitch = -mSpeed;
            }
        }
        if (mThrottle != 0) {
            if (mThrottle > 0) {
                mThrottle = mSpeed;
            } else {
                mThrottle = -mSpeed;
            }
        }
        if (mRoll != 0) {
            if (mRoll > 0) {
                mRoll = mSpeed;
            } else {
                mRoll = -mSpeed;
            }
        }
    }

    /**
     * Destroy the instance
     */
    public static void destroyInstance() {
        if (uniqueInstance != null) {
            uniqueInstance.mStop();

            if (uniqueInstance.mSendVirtualStickDataTimer != null) {
                uniqueInstance.mSendVirtualStickDataTask.cancel();
                uniqueInstance.mSendVirtualStickDataTask = null;
                uniqueInstance.mSendVirtualStickDataTimer.cancel();
                uniqueInstance.mSendVirtualStickDataTimer.purge();
                uniqueInstance.mSendVirtualStickDataTimer = null;
            }

            MyChangeSettingsExecutor.mDisableVS();

            uniqueInstance = null;
        }
    }
    //endregion

    //region 任务执行器以及计时器

    /**
     * 回调接口
     */
    public interface CommandCompletionCallback {
        /**
         * 当飞行指令完成时调用此方法。
         *
         * @param error 如果执行成功，error 为 null；如果执行失败，error 包含失败原因。
         */
        void onComplete(DJIError error);
    }

    /**
     * 检查数据并且发送数据，50毫秒发送一次指令
     */
    private void checkSendVirtualStickDataTimer() {
        if (mSendVirtualStickDataTimer == null) {
            mSendVirtualStickDataTask = new SendVirtualStickDataTask();
            mSendVirtualStickDataTimer = new Timer();
            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 50);
        }
    }

    /**
     * 高度位置追踪计时器
     * destroy timer first
     *
     * @param mode flight mode
     * @param homeH
     * @param tarH
     */
    private void checkHeightLocationTrackTimer(MyVirtualStickExecutorMode mode, double homeH, double tarH) {
        destroyLocationTrackTimer();
        if (mode == MyVirtualStickExecutorMode.UP_DIS || mode == MyVirtualStickExecutorMode.DOWN_DIS) {
            mLocationTrackTask = new LocationTrackTask(mode, homeH, tarH);
        }
        mLocationTrackTimer = new Timer();
        mLocationTrackTimer.schedule(mLocationTrackTask, 200, 200);
    }

    /**
     * 经纬度位置追踪计时器
     * destroy timer first
     *
     * @param mode
     * @param homeLat
     * @param homeLog
     * @param tarLat
     * @param tarLog
     */
    private void check2DLocationTrackTimer(MyVirtualStickExecutorMode mode, double homeLat, double homeLog, double tarLat, double tarLog) {
        destroyLocationTrackTimer();
        if (mode == MyVirtualStickExecutorMode.MOVE_DIS || mode == MyVirtualStickExecutorMode.FLY_TO) {
            mLocationTrackTask = new LocationTrackTask(mode, homeLat, homeLog, tarLat, tarLog);
        }
        mLocationTrackTimer = new Timer();
        mLocationTrackTimer.schedule(mLocationTrackTask, 200, 200);
    }

    /**
     * 高度位置追踪计时器回调
     * destroy timer first
     *
     * @param mode flight mode
     * @param homeH
     * @param tarH
     */
    private void checkHeightLocationTrackTimer(MyVirtualStickExecutorMode mode, double homeH, double tarH,CommandCompletionCallback callback) {
        destroyLocationTrackTimer();
        if (mode == MyVirtualStickExecutorMode.UP_DIS || mode == MyVirtualStickExecutorMode.DOWN_DIS) {
            mLocationTrackTask = new LocationTrackTask(mode, homeH, tarH, callback);
        }
        mLocationTrackTimer = new Timer();
        mLocationTrackTimer.schedule(mLocationTrackTask, 200, 200);
    }

    /**
     * 经纬度位置追踪计时器回调
     * destroy timer first
     *
     * @param mode
     * @param homeLat
     * @param homeLog
     * @param tarLat
     * @param tarLog
     */
    private void check2DLocationTrackTimer(MyVirtualStickExecutorMode mode, double homeLat, double homeLog, double tarLat, double tarLog,CommandCompletionCallback callback) {
        destroyLocationTrackTimer();
        if (mode == MyVirtualStickExecutorMode.MOVE_DIS || mode == MyVirtualStickExecutorMode.FLY_TO) {
            mLocationTrackTask = new LocationTrackTask(mode, homeLat, homeLog, tarLat, tarLog, callback);
        }
        mLocationTrackTimer = new Timer();
        mLocationTrackTimer.schedule(mLocationTrackTask, 200, 200);
    }

    /**
     * 发送虚拟控制指令
     */
    class SendVirtualStickDataTask extends TimerTask {
        @Override
        public void run() {
            if (mFlightController != null) {
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        }
                );
            }
        }
    }

    /**
     * 检查当前位置并且发送四个角
     * when need
     */
    class LocationTrackTask extends TimerTask {
        private MyVirtualStickExecutorMode m;
        private double homeH, tarH, curH, homeLat, homeLog, tarLat, tarLog, curLat, curLog;
        private CommandCompletionCallback mCallback; // 添加回调

        public LocationTrackTask(MyVirtualStickExecutorMode mode, double homeH, double tarH) {
            this.m = mode;
            this.homeH = homeH;
            this.tarH = tarH;
        }

        public LocationTrackTask(MyVirtualStickExecutorMode mode, double homeLat, double homeLog, double tarLat, double tarLog) {
            this.m = mode;
            this.homeLat = homeLat;
            this.homeLog = homeLog;
            this.tarLat = tarLat;
            this.tarLog = tarLog;
        }

        public LocationTrackTask(MyVirtualStickExecutorMode mode, double homeH, double tarH, CommandCompletionCallback callback) {
            this.m = mode;
            this.homeH = homeH;
            this.tarH = tarH;
            this.mCallback = callback;
        }

        public LocationTrackTask(MyVirtualStickExecutorMode mode, double homeLat, double homeLog, double tarLat, double tarLog, CommandCompletionCallback callback) {
            this.m = mode;
            this.homeLat = homeLat;
            this.homeLog = homeLog;
            this.tarLat = tarLat;
            this.tarLog = tarLog;
            this.mCallback = callback;
        }

        @Override
        public void run() {
            if (mFlightController != null) {
                if (m == MyVirtualStickExecutorMode.UP_DIS || m == MyVirtualStickExecutorMode.DOWN_DIS) {
                    curH = getCurrentAltitude();
                    double home2cur = curH - homeH;
                    double cur2tar = tarH - curH;
                    if (Math.abs(cur2tar) <= 0.5 || home2cur * cur2tar < 0) {
                        mThrottle = 0;
                        secFlag = false;

                        // 取消定时器任务
                        this.cancel();
                        destroyLocationTrackTimer();

                        // 调用回调，指令成功完成
                        if (mCallback != null) {
                            mCallback.onComplete(null);
                        }
                    } else if (Math.abs(cur2tar) < Math.abs(mThrottle)) {
                        if (secFlag) {
                            if (mThrottle > 0) {
                                mThrottle = 1;
                            } else {
                                mThrottle = -1;
                            }
                        }
                    }
                } else if (m == MyVirtualStickExecutorMode.MOVE_DIS || m == MyVirtualStickExecutorMode.FLY_TO) {
                    curLat = mFlightController.getState().getAircraftLocation().getLatitude();
                    curLog = mFlightController.getState().getAircraftLocation().getLongitude();

                    double home2curX = curLat - homeLat;
                    double home2curY = curLog - homeLog;
                    double home2curMag = Math.sqrt(home2curX * home2curX + home2curY * home2curY);

                    double cur2tarX = tarLat - curLat;
                    double cur2tarY = tarLog - curLog;
                    double cur2tarMag = Math.sqrt(cur2tarX * cur2tarX + cur2tarY * cur2tarY);

                    double cosUp = home2curX * cur2tarX + home2curY * cur2tarY;
                    double cosDown = home2curMag * cur2tarMag;

                    // For precise stopping and hovering
                    double dist = Utils.calcDistance(curLat, curLog, tarLat, tarLog);

                    if (dist < 0.33 || cosUp / cosDown < 0) {
                        mPitch = 0;
                        mRoll = 0;
                        secFlag = false;

                        // 取消定时器任务
                        this.cancel();
                        destroyLocationTrackTimer();

                        // 调用回调，指令成功完成
                        if (mCallback != null) {
                            mCallback.onComplete(null);
                        }
                    } else if (secFlag) {
                        if (dist < Math.abs(mPitch) || dist < Math.abs(mRoll)) {
                            if (mPitch != 0) {
                                mPitch = 1;
                            }
                            if (mRoll != 0) {
                                mRoll = 1;
                            }
                        } else if (m == MyVirtualStickExecutorMode.FLY_TO) {
                            //set direction
                            mYaw = (float) Utils.calcBearing(curLat, curLog, tarLat, tarLog);
                        }
                    }
                }
            }
        }
    }

    /**
     * destroy LocationTrackTimer when it is no need
     */
    private void destroyLocationTrackTimer() {
        if (mLocationTrackTimer != null) {
            mLocationTrackTask.cancel();
            mLocationTrackTask = null;
            mLocationTrackTimer.cancel();
            mLocationTrackTimer.purge();
            mLocationTrackTimer = null;
        }
    }

    private boolean secFlag = true;

    //endregion

    //region 飞行指令
    //TODO 更改延迟，优化代码
    /**
     * Stop
     */
    public void mStop() {
        mMode = MyVirtualStickExecutorMode.STOP;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        mPitch = 0;
        mRoll = 0;
        mThrottle = 0;
    }

    /**
     * Up
     */
    public void mUp(int dis) {
        mMode = MyVirtualStickExecutorMode.UP_WITHOUT_DIS;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        mThrottle = mSpeed;
        if (dis != -1) {
            secFlag = true;
            mMode = MyVirtualStickExecutorMode.UP_DIS;
            double homeH = getCurrentAltitude();
            double tarH = homeH + dis;
            checkHeightLocationTrackTimer(mMode, homeH, tarH);
        }
    }

    /**
     * Down
     */
    public void mDown(int dis) {
        mMode = MyVirtualStickExecutorMode.DOWN_WITHOUT_DIS;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        mThrottle = -mSpeed;
        if (dis != -1) {
            secFlag = true;
            mMode = MyVirtualStickExecutorMode.DOWN_DIS;
            double homeH = getCurrentAltitude();
            double tarH = homeH - dis;

            if (homeH < 1.2 || tarH <= 0) {
                mFlightController.startLanding(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {

                    }
                });
            } else {
                checkHeightLocationTrackTimer(mMode, homeH, tarH);
            }
        }
    }

    /**
     * Turn
     */
    public void mTurn(int turningDirection, int optionalTurningDegree) {
        mMode = MyVirtualStickExecutorMode.TURN;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        float currHeading = mFlightController.getCompass().getHeading();
        mYaw = currHeading;
        if (turningDirection == 303) {
            mYaw -= optionalTurningDegree;
            if (mYaw < -180) {
                mYaw += 360;
            }
        } else {
            mYaw += optionalTurningDegree;
            if (mYaw > 180) {
                mYaw -= 360;
            }
        }
    }

    /**
     * Move
     */
    public void mGo(int movingDirection, double optionalDis) {
        mMode = MyVirtualStickExecutorMode.MOVE_WITHOUT_DIS;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();

        int dir[] = {0, 1, 0, -1, 0};
        int idx = 0;
        if (movingDirection == 302) {
            idx = 2;
        } else if (movingDirection == 303) {
            idx = 3;
        } else if (movingDirection == 304) {
            idx = 1;
        }
        mPitch = (float) (mSpeed * dir[idx]);
        mRoll = (float) (mSpeed * dir[idx + 1]);

        if (optionalDis != -1) {
            secFlag = true;
            mMode = MyVirtualStickExecutorMode.MOVE_DIS;
            double bearing = mFlightController.getCompass().getHeading();
            if (movingDirection == 302) {
                bearing -= 180;
            } else if (movingDirection == 303) {
                bearing -= 90;
            } else if (movingDirection == 304) {
                bearing += 90;
            }
            double homeLat = mFlightController.getState().getAircraftLocation().getLatitude();
            double homeLog = mFlightController.getState().getAircraftLocation().getLongitude();
            double tar[] = Utils.calcDestination(homeLat, homeLog, bearing, optionalDis);
            check2DLocationTrackTimer(mMode, homeLat, homeLog, tar[0], tar[1]);
        }
    }

    /**
     * Fly to a specific location
     */
    public void mFlyto(double tarLat, double tarLog) {
        final double initLati = mFlightController.getState().getAircraftLocation().getLatitude();
        final double initLongi = mFlightController.getState().getAircraftLocation().getLongitude();
        final double destLati = tarLat;
        final double destLogi = tarLog;
        final float targetBearing = (float) Utils.calcBearing(initLati, initLongi, destLati, destLogi);
        mMode = MyVirtualStickExecutorMode.TURN;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        mYaw = targetBearing;
        new Thread(new Runnable() {
            public void run() {
                while (Math.abs(mFlightController.getCompass().getHeading() - targetBearing) > 1) {
                }
                secFlag = true;
                mRoll = 10;
                mMode = MyVirtualStickExecutorMode.MOVE_DIS;
                check2DLocationTrackTimer(mMode, initLati, initLongi, destLati, destLogi);
            }
        }).start();
    }
    //endregion

    //region 异步回调飞行指令
    //TODO 更改延迟，优化代码
    /**
     * Take off 起飞
     */
    public void mTakeoff(CommandCompletionCallback callback) {
        if (mFlightController != null) {
            mFlightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        // 调用回调，指令成功完成
                        if (callback != null) {
                            callback.onComplete(null);
                        }
                        Log.d("DroneOperation", "Takeoff successful.");

                    } else {
                        Log.e("DroneOperation", "Takeoff failed: " + djiError.getDescription());
                    }
                }
            });
        } else {
            Log.e("DroneOperation", "Flight controller is not initialized.");
        }
    }

    /**
     * Landing 着陆
     */
    public void mLand(CommandCompletionCallback callback) {
        if (mFlightController != null) {
            mFlightController.startLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        // 调用回调，指令成功完成
                        if (callback != null) {
                            callback.onComplete(null);
                        }
                        Log.d("DroneOperation", "Landing successful.");
                    } else {
                        Log.e("DroneOperation", "Landing failed: " + djiError.getDescription());
                    }
                }
            });
        } else {
            Log.e("DroneOperation", "Flight controller is not initialized.");
        }
    }

    /**
     * Up
     */
    public void mUp(int dis,CommandCompletionCallback callback) {
        mMode = MyVirtualStickExecutorMode.UP_WITHOUT_DIS;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        mThrottle = mSpeed;
        if (dis != -1) {
            secFlag = true;
            mMode = MyVirtualStickExecutorMode.UP_DIS;
            double homeH = getCurrentAltitude();
            double tarH = homeH + dis;
            checkHeightLocationTrackTimer(mMode, homeH, tarH,callback);
        } else {
            if (callback != null) {
                callback.onComplete(null);
            }
        }
    }

    /**
     * Down
     */
    public void mDown(int dis,CommandCompletionCallback callback) {
        mMode = MyVirtualStickExecutorMode.DOWN_WITHOUT_DIS;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        mThrottle = -mSpeed;
        if (dis != -1) {
            secFlag = true;
            mMode = MyVirtualStickExecutorMode.DOWN_DIS;
            double homeH = getCurrentAltitude();
            double tarH = homeH - dis;

            if (homeH < 1.2 || tarH <= 0) {
                mFlightController.startLanding(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {

                    }
                });
            } else {
                checkHeightLocationTrackTimer(mMode, homeH, tarH,callback);
            }
        } else {
            if (callback != null) {
                callback.onComplete(null);
            }
        }
    }

    /**
     * Turn
     */
    public void mTurn(int turningDirection, int optionalTurningDegree, CommandCompletionCallback callback) {
        mMode = MyVirtualStickExecutorMode.TURN;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        float currHeading = mFlightController.getCompass().getHeading();
        mYaw = currHeading;
        if (turningDirection == 303) {
            mYaw -= optionalTurningDegree;
            if (mYaw < -180) {
                mYaw += 360;
            }
        } else {
            mYaw += optionalTurningDegree;
            if (mYaw > 180) {
                mYaw -= 360;
            }
        }

        // 转向完成后直接调用回调
        if (callback != null) {
            callback.onComplete(null);
            Log.d("DroneOperation", "Turn successful.");
        }
    }

    /**
     * Move
     */
    public void mGo(int movingDirection, double optionalDis ,CommandCompletionCallback callback) {
        mMode = MyVirtualStickExecutorMode.MOVE_WITHOUT_DIS;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();

        int dir[] = {0, 1, 0, -1, 0};
        int idx = 0;
        if (movingDirection == 302) {
            idx = 2;
        } else if (movingDirection == 303) {
            idx = 3;
        } else if (movingDirection == 304) {
            idx = 1;
        }
        mPitch = (float) (mSpeed * dir[idx]);
        mRoll = (float) (mSpeed * dir[idx + 1]);

        if (optionalDis != -1) {
            secFlag = true;
            mMode = MyVirtualStickExecutorMode.MOVE_DIS;
            double bearing = mFlightController.getCompass().getHeading();
            if (movingDirection == 302) {
                bearing -= 180;
            } else if (movingDirection == 303) {
                bearing -= 90;
            } else if (movingDirection == 304) {
                bearing += 90;
            }
            double homeLat = mFlightController.getState().getAircraftLocation().getLatitude();
            double homeLog = mFlightController.getState().getAircraftLocation().getLongitude();
            double tar[] = Utils.calcDestination(homeLat, homeLog, bearing, optionalDis);
            check2DLocationTrackTimer(mMode, homeLat, homeLog, tar[0], tar[1], callback);
        } else {
            if (callback != null) {
                callback.onComplete(null);
            }
        }
    }

    /**
     * Fly to a specific location
     */
    public void mFlyto(double tarLat, double tarLog, CommandCompletionCallback callback) {
        final double initLati = mFlightController.getState().getAircraftLocation().getLatitude();
        final double initLongi = mFlightController.getState().getAircraftLocation().getLongitude();
        final double destLati = tarLat;
        final double destLogi = tarLog;
        final float targetBearing = (float) Utils.calcBearing(initLati, initLongi, destLati, destLogi);
        mMode = MyVirtualStickExecutorMode.TURN;
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        mYaw = targetBearing;
        new Thread(new Runnable() {
            public void run() {
                while (Math.abs(mFlightController.getCompass().getHeading() - targetBearing) > 1) {
                    try {
                        Thread.sleep(100); // 添加延迟，避免空循环占用过多 CPU
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                secFlag = true;
                mRoll = 10;
                mMode = MyVirtualStickExecutorMode.MOVE_DIS;
                check2DLocationTrackTimer(mMode, initLati, initLongi, destLati, destLogi, callback);
            }
        }).start();
    }
    //endregion

    //region 具体命令回调实现

    /**
     * 飞控接口
     */
    public interface DroneCommand {
        /**
         * 执行命令。
         *
         * @param callback 命令完成后的回调。
         */
        void execute(CommandCompletionCallback callback);
    }

    /**
     * Take off 起飞命令
     */
    public class TakeoffCommand implements DroneCommand {
        @Override
        public void execute(CommandCompletionCallback callback) {
            mTakeoff(callback);
        }
    }

    /**
     * Landing 着陆命令
     */
    public class LandCommand implements DroneCommand {
        @Override
        public void execute(CommandCompletionCallback callback) {
            mLand(callback);
        }
    }

    /**
     * Up 上升命令
     */
    public class UpCommand implements DroneCommand {
        private int distance;

        public UpCommand(int distance) {
            this.distance = distance;
        }

        @Override
        public void execute(CommandCompletionCallback callback) {
            mUp(distance, callback);
        }
    }

    /**
     * Down 下降命令
     */
    public class DownCommand implements DroneCommand {
        private int distance;

        public DownCommand(int distance) {
            this.distance = distance;
        }

        @Override
        public void execute(CommandCompletionCallback callback) {
            mDown(distance, callback);
        }
    }

    /**
     * Turn 转向命令
     */
    public class TurnCommand implements DroneCommand {
        private int turningDirection;
        private int turningDegree;

        public TurnCommand(int turningDirection, int turningDegree) {
            this.turningDirection = turningDirection;
            this.turningDegree = turningDegree;
        }

        @Override
        public void execute(CommandCompletionCallback callback) {
            mTurn(turningDirection, turningDegree, callback);
        }
    }

    /**
     * Go 移动命令
     */
    public class GoCommand implements DroneCommand {
        private int movingDirection;
        private double distance;

        public GoCommand(int movingDirection, double distance) {
            this.movingDirection = movingDirection;
            this.distance = distance;
        }

        @Override
        public void execute(CommandCompletionCallback callback) {
            mGo(movingDirection, distance, callback);
        }
    }

    /**
     * FlyTo 飞往指定位置命令
     */
    public class FlyToCommand implements DroneCommand {
        private double tarLat;
        private double tarLog;

        public FlyToCommand(double tarLat, double tarLog) {
            this.tarLat = tarLat;
            this.tarLog = tarLog;
        }

        @Override
        public void execute(CommandCompletionCallback callback) {
            mFlyto(tarLat, tarLog, callback);
        }
    }

    //endregion

    //region 命令队列管理

    /**
     * 添加命令到队列。
     *
     * @param command 要添加的命令。
     */
    public void enqueueCommand(DroneCommand command) {
        commandQueue.add(command);
    }

    /**
     * 开始执行命令队列。
     *
     * @param finalCallback 所有命令执行完毕后的回调。
     */
    public void executeCommandQueue(CommandCompletionCallback finalCallback) {
        if (!isExecuting && !commandQueue.isEmpty()) {
            isExecuting = true;
            executeNextCommand(finalCallback);
        }
    }

    /**
     * 执行下一个命令。
     *
     * @param finalCallback 所有命令执行完毕后的回调。
     */
    private void executeNextCommand(CommandCompletionCallback finalCallback) {
        if (commandQueue.isEmpty()) {
            isExecuting = false;
            // 所有命令执行完毕，触发最终回调
            if (finalCallback != null) {
                finalCallback.onComplete(null);
            }
            return;
        }

        DroneCommand command = commandQueue.poll();
        if (command != null) {
            command.execute(new CommandCompletionCallback() {
                @Override
                public void onComplete(DJIError error) {
                    if (error == null) {
                        // 当前命令成功，执行下一个命令
                        executeNextCommand(finalCallback);
                    } else {
                        // 当前命令失败，触发最终回调并停止执行
                        isExecuting = false;
                        if (finalCallback != null) {
                            finalCallback.onComplete(error);
                        }
                        // 记录错误日志
                        Log.e("DroneCommandExecutor", "Command failed: " + error.getDescription());
                    }
                }
            });
        }
    }

    //endregion

}