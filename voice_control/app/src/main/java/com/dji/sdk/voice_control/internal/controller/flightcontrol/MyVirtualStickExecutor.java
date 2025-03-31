package com.dji.sdk.voice_control.internal.controller.flightcontrol;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.controller.utils.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;

/**
 * Main flight control module
 */
public class MyVirtualStickExecutor {

    //region 控制数据结构
    //设置虚拟控制指令模式
    private MyVirtualStickExecutorMode mMode = MyVirtualStickExecutorMode.UNINITIALIZED;
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
    private MyVirtualStickExecutor() {
    }
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
        if (mode == MyVirtualStickExecutorMode.MOVE_DIS || mode == MyVirtualStickExecutorMode.FLY_TO || mode == MyVirtualStickExecutorMode.MOVE_DIS_SPEED) {
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

        // 添加调试日志文件相关属性
        private FileWriter debugLogWriter;
        private SimpleDateFormat dateFormat;
        private String logFilePath;
        private int runCounter = 0;

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
            runCounter++;

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


                    if (cosDown != 0) {
                    }

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
                            float previousYaw = mYaw;
                            mYaw = (float) Utils.calcBearing(curLat, curLog, tarLat, tarLog);
                        }
                    }
                } else if (m == MyVirtualStickExecutorMode.MOVE_DIS_SPEED) {
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

                    if (cosDown != 0) {
                    }

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
                        }
                    }
                }
            } else {
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
        if(optionalDis>2){
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
        } else{
            simpleMove(movingDirection,optionalDis);
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

    //region 飞行指令
    //TODO 更改延迟，优化代码

    /**
     * move with yaw and speed
     */
    public void mMovewith(float yaw, float roll) {
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();

        mYaw = yaw;
        mRoll = roll;
    }

    /**
     * move with yaw and speed
     */
    public void mMovexya(float yaw, float roll, float pitch) {
        checkSendVirtualStickDataTimer();
        destroyLocationTrackTimer();
        mYaw = yaw;
        mRoll = roll;
        mPitch = pitch;
    }

    /**
     * 执行简单移动指令（前后左右移动固定距离）
     * @param direction 移动方向 (1=前, 2=后, 3=左, 4=右)
     * @param distance 移动距离（米）
     */
    public void simpleMove(int direction, double distance) {
        // 停止当前所有移动
        mStop();

        // 根据方向设置控制值
        switch (direction) {
            case 301: // 前
                mRoll = (float) distance/2; // 正值向前
                break;
            case 302: // 后
                mRoll = (float) -distance/2; // 负值向后
                break;
            case 303: // 左
                mPitch = (float) -distance/2; // 负值向左
                break;
            case 304: // 右
                mPitch = (float) distance/2; // 正值向右
                break;
        }

        // 设置定时器，1秒后归零
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                mPitch = 0;
                mRoll = 0;
            }
        }, 2000); // 1秒后执行
    }
    //endregion

}