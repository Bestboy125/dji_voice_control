package com.dji.sdk.voice_control.internal.controller.utils;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 *  Math and Display Utils
 */


public class Utils {
    private static final String TAG = "Utils";
    public static final double ONE_METER_OFFSET = 0.00000899322;
    private static long lastClickTime;
    private static Handler mUIHandler = new Handler(Looper.getMainLooper());

    /**
     * UI Utils
     */
    public static boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        long timeD = time - lastClickTime;
        if ( 0 < timeD && timeD < 800) {
            return true;
        }
        lastClickTime = time;
        return false;
    }

    public static void setResultToToast(final Context context, final String string) {
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void setResultToText(final Context context, final TextView tv, final String s) {
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tv == null) {
                    Toast.makeText(context, "tv is null", Toast.LENGTH_SHORT).show();
                } else {
                    tv.setText(s);
                }
            }
        });
    }
    /**
     * END of UI Utils
     */

    /**
     * Math Utils
     */
    /**
     * Calculate destination coordinate by origin coordinate, bearing and distance
     *
     * @param lati latitude of origin point
     * @param longi longitude of origin point
     * @param bearing initial bearing of the drone
     * @param distance distance from the origin point toward the given bearing
     * @return Destination Coordinate
     */
    public static double[] calcDestination(double lati, double longi,
                                           double bearing, double distance) {
        double[] destination = new double[2]; // double[0]=latitude double[1]=longitude

        // Setup parameters
        double radius = 6371000; // Earth radius in meters
        double ber = bearing; // Heading direction, clockwise from north
        if (bearing < 0) {
            ber += 360;
        }
        ber = Math.toRadians(ber);
        double oriLati = Math.toRadians(lati); // Latitude of the origin point
        double oriLongi = Math.toRadians(longi); // Longitude of the origin point
        double agDist = distance / radius; // Angular distance
        destination[0] = Math.asin(Math.sin(oriLati) * Math.cos(agDist)
                + Math.cos(oriLati) * Math.sin(agDist) * Math.cos(ber));
        destination[1] = oriLongi
                + Math.atan2(
                Math.sin(ber) * Math.sin(agDist) * Math.cos(oriLati),
                Math.cos(agDist) - Math.sin(oriLati)
                        * Math.sin(destination[0]));
        destination[0] = Math.toDegrees(destination[0]);
        destination[1] = Math.toDegrees(destination[1]);
        return destination;
    }

    /**
     * Calculate the bearing between two geolocation
     *
     * @param initLati latitude of origin point
     * @param initLongi longitude of origin point
     * @param destLati latitude of destination point
     * @param destLongi longitude of destination point
     * @return bearing (turning direction)
     */
    public static double calcBearing(double initLati, double initLongi, double destLati, double destLongi){
        initLati=Math.toRadians(initLati);
        destLati=Math.toRadians(destLati);
        initLongi=Math.toRadians(initLongi);
        destLongi=Math.toRadians(destLongi);
        double deltaLongi = destLongi-initLongi;
        double y = Math.sin(deltaLongi)*Math.cos(destLati);
        double x = Math.cos(initLati)*Math.sin(destLati)-Math.sin(initLati)*Math.cos(destLati)*Math.cos(deltaLongi);
        double bearing = Math.toDegrees(Math.atan2(y,x));
        return bearing;
    }

    /**
     * Calculate the distance between two geolocation
     *
     * @param initLati latitude of origin point
     * @param initLongi longitude of origin point
     * @param destLati latitude of destination point
     * @param destLongi longitude of destination point
     * @return distance between origin point and destination point
     */
    public static double calcDistance(double initLati, double initLongi, double destLati, double destLongi){
        double radius = 6371000;
        initLati=Math.toRadians(initLati);
        destLati=Math.toRadians(destLati);
        initLongi=Math.toRadians(initLongi);
        destLongi=Math.toRadians(destLongi);
        double deltaLati = destLati-initLati;
        double deltaLongi = destLongi-initLongi;
        double a = Math.sin(deltaLati/2)*Math.sin(deltaLati/2)+Math.cos(initLati)*Math.cos(destLati)*Math.sin(deltaLongi/2)*Math.sin(deltaLongi/2);
        double c = 2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
        double distance=radius*c;
        return distance;
    }
    /**
     * END of Math Utils
     */

    /**
     * @unused save for later usage
     */

    //region 阿里云
    public static String ip = "";
    public static int createDir (String dirPath) {
        File dir = new File(dirPath);
        //文件夹是否已经存在
        if (dir.exists()) {
            Log.w(TAG,"The directory [ " + dirPath + " ] has already exists");
            return 1;
        }

        if (!dirPath.endsWith(File.separator)) {//不是以 路径分隔符 "/" 结束，则添加路径分隔符 "/"
            dirPath = dirPath + File.separator;
        }

        //创建文件夹
        if (dir.mkdirs()) {
            Log.d(TAG,"create directory [ "+ dirPath + " ] success");
            return 0;
        }

        Log.e(TAG,"create directory [ "+ dirPath + " ] failed");
        return -1;
    }

    /** Returns the consumer friendly device name */
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    public static String getDeviceId() {
        return android.os.Build.SERIAL;
    }

//    public static String getDirectIp() {
//        Log.i(TAG, "direct ip is " + Utils.ip);
//        Thread th = new Thread(){
//            @Override
//            public void run() {
//                try {
//                    InetAddress addr = InetAddress.getByName("nls-gateway-inner.aliyuncs.com");
//                    Utils.ip = addr.getHostAddress();
//                    Log.i(TAG, "direct ip is " + Utils.ip);
//                } catch (UnknownHostException e) {
//                    e.printStackTrace();
//                }
//            }
//
//        };
//        th.start();
//        try {
//            th.join(5000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        return ip;
//    }

    public static String getMsgWithErrorCode(int code, String status) {
        String str = "错误码:" + code;
        switch (code) {
            case 140001:
                str += " 错误信息: 引擎未创建, 请检查是否成功初始化, 详情可查看运行日志.";
                break;
            case 140008:
                str += " 错误信息: 鉴权失败, 请关注日志中详细失败原因.";
                break;
            case 140011:
                str += " 错误信息: 当前方法调用不符合当前状态, 比如在未初始化情况下调用pause接口.";
                break;
            case 140013:
                str += " 错误信息: 当前方法调用不符合当前状态, 比如在未初始化情况下调用pause/release等接口.";
                break;
            case 140900:
                str += " 错误信息: tts引擎初始化失败, 请检查资源路径和资源文件是否正确.";
                break;
            case 140901:
                str += " 错误信息: tts引擎初始化失败, 请检查使用的SDK是否支持离线语音合成功能.";
                break;
            case 140903:
                str += " 错误信息: tts引擎任务创建失败, 请检查资源路径和资源文件是否正确.";
                break;
            case 140908:
                str += " 错误信息: 发音人资源无法获得正确采样率, 请检查发音人资源是否正确.";
                break;
            case 140910:
                str += " 错误信息: 发音人资源路径无效, 请检查发音人资源文件路径是否正确.";
                break;
            case 144002:
                str += " 错误信息: 若发生于语音合成, 可能为传入文本超过16KB. 可升级到最新版本, 具体查看日志确认.";
                break;
            case 144003:
                str += " 错误信息: token过期或无效, 请检查token是否有效.";
                break;
            case 144004:
                str += " 错误信息: 语音合成超时, 具体查看日志确认.";
                break;
            case 144006:
                str += " 错误信息: 云端返回未分类错误, 请看详细的错误信息.";
                break;
            case 144103:
                str += " 错误信息: 设置参数无效, 请参考接口文档检查参数是否正确, 也可通过task_id咨询客服.";
                break;
            case 170008:
                str += " 错误信息: 鉴权成功, 但是存储鉴权信息的文件路径不存在或无权限.";
                break;
            case 170806:
                str += " 错误信息: 请设置SecurityToken.";
                break;
            case 170807:
                str += " 错误信息: SecurityToken过期或无效, 请检查SecurityToken是否有效.";
                break;
            case 240005:
                if (status == "init") {
                    str += " 错误信息: 请检查appkey、akId、akSecret等初始化参数是否无效或空.";
                } else {
                    str += " 错误信息: 传入参数无效, 请检查参数正确性.";
                }
                break;
            case 240011:
                str += " 错误信息: SDK未成功初始化.";
                break;
            case 240040:
                str += " 错误信息: 本地引擎初始化失败，可能是资源文件(如kws.bin)损坏.";
                break;
            case 240052:
                str += " 错误信息: 2s未传入音频数据，请检查录音相关代码、权限或录音模块是否被其他应用占用.";
                break;
            case 240063:
                str += " 错误信息: SSL错误，可能为SSL建连失败。比如token无效或者过期，或SSL证书校验失败(可升级到最新版)等等，具体查日志确认.";
                break;
            case 240068:
                str += " 错误信息: 403 Forbidden, token无效或者过期.";
                break;
            case 240070:
                str += " 错误信息: 鉴权失败, 请查看日志确定具体问题, 特别是关注日志 E/iDST::ErrMgr: errcode=.";
                break;
            case 41010105:
                str += " 错误信息: 长时间未收到人声，触发静音超时.";
                break;
            case 999999:
                str += " 错误信息: 库加载失败, 可能是库不支持当前activity, 或库加载时崩溃, 可详细查看日志判断.";
                break;
            default:
                str += " 未知错误信息, 请查看官网错误码和运行日志确认问题.";
        }
        return str;
    }

    public static boolean isExist(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            Log.e(TAG, "打不开：" + filename);
            return false;
        } else {
            return true;
        }
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }

        return phrase.toString();
    }
    //endregion
//    public static boolean checkGpsCoordinate(double latitude, double longitude) {
//        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
//    }
//
//    public static double cosForDegree(double degree) {
//        return Math.cos(degree * Math.PI / 180.0f);
//    }
//
//    public static double calcLongitudeOffset(double latitude) {
//        return ONE_METER_OFFSET / cosForDegree(latitude);
//    }
//
//    public static void addLineToSB(StringBuffer sb, String name, Object value) {
//        if (sb == null) return;
//        sb.
//                append(name == null ? "" : name + ": ").
//                append(value == null ? "" : value + "").
//                append("\n");
//    }
}
