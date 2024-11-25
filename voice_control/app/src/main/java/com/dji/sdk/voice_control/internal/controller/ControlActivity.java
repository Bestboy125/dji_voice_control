package com.dji.sdk.voice_control.internal.controller;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.voice_control.BatteryView;
import com.dji.sdk.voice_control.internal.controller.voice_control.CommandClassifier;
import com.dji.sdk.voice_control.internal.controller.voice_control.CommandConfirmationDialogFragment;
import com.dji.sdk.voice_control.internal.controller.voice_control.PlaceListFragment;
import com.dji.sdk.voice_control.internal.controller.waypoint.Waypoint2Activity;
import com.dji.sdk.voice_control.internal.utils.JsonParser;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;

import org.json.JSONException;
import org.json.JSONObject;

import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

import com.iflytek.cloud.SpeechUtility;

public class ControlActivity extends AppCompatActivity implements View.OnClickListener,CommandConfirmationDialogFragment.Communicator {
    //UI数据结构
    //日志
    private static final String TAG = MainActivity.class.getName();
    //权限列表
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    //缺失权限
    private List<String> missingPermission = new ArrayList<>();
    //是被注册在过程中
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    //飞行控制器
    private FlightController mFlightController;
    //UI控件
    protected TextView mConnectStatusTextView;
    private Button mBtnEnableVirtualStick;
    private Button mBtnDisableVirtualStick;
    private ToggleButton mBtnSimulator;
    private Button mBtnTakeOff;
    private Button mBtnLand;
    private Button mBtnSub;
    private EditText mCMD;
    private CommandClassifier cc1;
    private Button mBtnPhoto;
    private Button mBtnDownload;
    private Button mBtnWaypoint;
    private Button mBtnLanguage;
    private Button mBtnLanguage2;
    private boolean languageType;
    private String language="en_us";

    private TextView mTextView;

    //Aircraft State 文字显示飞机的状态
    private TextView mAltitude;
    private TextView mVerSpeed;
    private TextView mHorSpeed;
    private TextView mDistance;
    //Battery 电池状态
    BatteryView mBatteryView;
    private TextView mBatteryData;
    private int mBatteryPercent;
    private Toast mToast;

    //虚拟摇杆
    private OnScreenJoystick mScreenJoystickRight;
    private OnScreenJoystick mScreenJoystickLeft;

    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;
    //飞行数据
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;
    private String mStrIntention;
    //消息控制器
    private Handler mHandler;
    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback;
    public static final String FLAG_CONNECTION_CHANGE = "com_dji_simulatorDemo_connection_change";
    int ret = 0;
    private TextView logTextView;
    //endregion
    //region 语音识别的数据结构
    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mIatDialog;
    //
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<>();
    private Button languageText, dialogButton;
    // 语言类型【默认中文】
    // 格式类型【默认json】
    private String resultType = "json";
    private boolean cyclic = false;//音频流识别是否循环调用
    //拼接字符串
    private StringBuffer buffer = new StringBuffer();
    //Handler码
    private int handlerCode = 0x123;
    // 函数调用返回值
    private int resultCode = 0;
    // 切换中英文
    // 弹框是否显示
    private int dialogType;
    //endregion

    //region 飞行控制的数据结构
    //该活动的实例
    private Context mContext;

    //命令行交互
    private CommandInterpreter mCI;

    // Map
    private View mMapView;
    private LatLng mDroneLocation = new LatLng(0, 0);
    private float mDroneHeading = 0;
    private Marker mDroneMarker = null;
    private LatLng mUserLocation = new LatLng(0, 0);
    //定位
    private Button mBtnLoacte;
    private boolean mMapLocate_flag = true;
    //追踪
    private Button mBtnTracking;
    private boolean mMapTracking_flag = true;

    private PlaceListFragment mPlaceListFragment;

    //private TextView mDistance; 存储飞机数据
    private double mAltitudeData;
    private double mvs;
    private double mhs;
    private double mdistToHome;
    //endregion

    //region 科大讯飞监视器
    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.e(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showToast("初始化失败，错误码：" + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            }
        }
    };

    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showToast("开始说话");
            updateLog("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            showToast(error.getPlainDescription(true));
            if (null != dialog) {
                dialog.dismiss();
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showToast("结束说话");
            updateLog("结束说话");
            if (null != dialog) {
                dialog.dismiss();
            }
            // Tokenize command_in_text
            StringTokenizer st = new StringTokenizer(mStrIntention);
            ArrayList<String> tokenedCommand = new ArrayList<>();
            while (st.hasMoreTokens()) {
                tokenedCommand.add(st.nextToken());
            }
            // Replace mavic similar words
//            tokenedCommand = findMavicSimilar(tokenedCommand);
            // Change arraylist to string
            mStrIntention = TextUtils.join(" ", tokenedCommand);
            // Execute NLC
            new ClassificationTask().execute(tokenedCommand);
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.e(TAG, "onResult: " + results.getResultString());
            if (resultType.equals(resultType)) {
            } else if (resultType.equals("plain")) {
                buffer.append(results.getResultString());
                mStrIntention = buffer.toString();
            }
            if (isLast & cyclic) {
                // TODO 最后的结果
                Message message = Message.obtain();
                message.what = handlerCode;
                handler.sendMessageDelayed(message, 100);
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            //showToast("当前正在说话，音量大小：" + volume);
            Log.e(TAG, "onVolumeChanged: " + data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            // if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //    String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //    Log.d(TAG, "session id =" + sid);
            // }
        }
    };

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == handlerCode) {
            }
        }
    };

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        /**
         * 识别回调成功
         */
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());
            String sn = null;
            // 读取json结果中的sn字段
            try {
                JSONObject resultJson = new JSONObject(results.getResultString());
                sn = resultJson.optString("sn");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mIatResults.put(sn, text);
            StringBuffer resultBuffer = new StringBuffer();
            for (String key : mIatResults.keySet()) {
                resultBuffer.append(mIatResults.get(key));
            }
            // 创建消息框
            AlertDialog.Builder builder = new AlertDialog.Builder(ControlActivity.this);
            builder.setTitle("确认识别结果");

            // 创建一个 EditText 以显示和编辑结果
            EditText editText = new EditText(ControlActivity.this);
            editText.setText(resultBuffer.toString());
            editText.setSelection(resultBuffer.toString().length()); // 光标移到文本末尾
            builder.setView(editText);

            // 设置确认按钮
            builder.setPositiveButton("确定", (dialog, which) -> {
                String confirmedResult = editText.getText().toString();
                // TODO: 在这里处理用户确认后的识别结果，例如更新 UI 或发送数据
                editText.setText(confirmedResult); // 假设将结果显示在 TextView 上
                Toast.makeText(getApplicationContext(), "结果已确认：" + confirmedResult, Toast.LENGTH_SHORT).show();
                processConfirmedResult(confirmedResult);
            });

            // 设置取消按钮
            builder.setNegativeButton("取消", (dialog, which) -> {
                dialog.dismiss(); // 直接关闭对话框
            });

            // 显示消息框
            builder.show();
        }

        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            showToast(error.getPlainDescription(true));
        }


    };
    // 处理确认后的结果并执行您的逻辑
    private void processConfirmedResult(String confirmedResult) {
        // 将确认结果转换为小写
        String mStrIntention = confirmedResult.toLowerCase();

        // 使用 StringTokenizer 对结果进行分词
        StringTokenizer st = new StringTokenizer(mStrIntention);
        ArrayList<String> tokenedCommand = new ArrayList<>();
        while (st.hasMoreTokens()) {
            tokenedCommand.add(st.nextToken());
        }

        // 如果需要，处理类似 mavic 的单词（这里注释掉了）
        // tokenedCommand = findMavicSimilar(tokenedCommand);

        // 将 ArrayList 转换为字符串
        mStrIntention = TextUtils.join(" ", tokenedCommand);

        // 执行分类任务（假设是执行 NLC 的核心逻辑）
        ClassificationTask cft = new ClassificationTask();
        cft.execute(tokenedCommand);
    }

    /**
     * 听写参数设置
     */
    public void setParam() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);
        // 设置听写引擎类型
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        // 设置返回结果格式【目前支持json,xml以及plain 三种格式，其中plain为纯听写文本内容】
        mIat.setParameter(SpeechConstant.RESULT_TYPE, resultType);
        //目前Android SDK支持zh_cn：中文、en_us：英文、ja_jp：日语、ko_kr：韩语、ru-ru：俄语、fr_fr：法语、es_es：西班牙语、
        // 注：小语种若未授权无法使用会报错11200，可到控制台-语音听写（流式版）-方言/语种处添加试用或购买。
        mIat.setParameter(SpeechConstant.LANGUAGE, language);
        // 设置语言区域、当前仅在LANGUAGE为简体中文时，支持方言选择，其他语言区域时，可把此参数值设为mandarin。
        // 默认值：mandarin，其他方言参数可在控制台方言一栏查看。
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
        //获取当前语言（同理set对应get方法）
        Log.e(TAG, "last language:" + mIat.getParameter(SpeechConstant.LANGUAGE));
        //此处用于设置dialog中不显示错误码信息
        //mIat.setParameter("view_tips_plain","false");
        //开始录入音频后，音频后面部分最长静音时长，取值范围[0,10000ms]，默认值5000ms
        mIat.setParameter(SpeechConstant.VAD_BOS, "5000");
        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音取值范围[0,10000ms]，默认值1800ms。
        mIat.setParameter(SpeechConstant.VAD_EOS, "1800");
        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, "1");
        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/helloword.wav");
    }


    /**
     * 展示吐司
     */
    private void showToast(final String str) {
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    private AlertDialog dialog;

    private void showAlertDialog() {
        dialog = new AlertDialog.Builder(this)
                .setTitle("自定弹框")//标题
                .setMessage("正在识别，请稍后...")//内容
                .setIcon(R.mipmap.ic_launcher)//图标
                .create();
        dialog.show();
    }

    private void showTip(final String str) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT);
        mToast.show();
    }
    //endregion

    //region watson文字分类
    /**
     * Initialize Watson Service
     * 初始化watson service
     */
    private SpeechToText initSpeechToTextService() {
        SpeechToText service = new SpeechToText();
        String username = "23c90b4b-23ee-43cc-b0e9-97f36a0c0cfc";
        String password = "X5zb8Ub0WKsH";
        service.setUsernameAndPassword(username, password);
        service.setEndPoint("https://stream.watsonplatform.net/speech-to-text/api");
        return service;
    }

    /**
     * Recognize Options 识别选项
     */
    private RecognizeOptions getRecognizeOptions() {
        return new RecognizeOptions.Builder()
                .continuous(true)
                .contentType(ContentType.OPUS.toString())
                .model("en-US_BroadbandModel")
                .interimResults(true)
                .customizationId("bf8c3a80-fba6-11e6-a1e7-a139b48a88e5")
                .inactivityTimeout(3000)
                .smartFormatting(true)
                .build();
    }

    /**
     * Classification Service 声音分类服务
     */

    public class ClassificationTask {

        private final ExecutorService executorService;
        private final Handler mainHandler;

        public ClassificationTask() {
            this.executorService = Executors.newSingleThreadExecutor();
            this.mainHandler = new Handler(Looper.getMainLooper());
        }

        public void execute(ArrayList params) {
            String result = doInBackground(params);
            // Post result back to main thread
        }

        private String doInBackground(ArrayList... params) {
            String result = null;
            if (params[0].size() != 0) {
                // call WatsonCommandClassifier to classify into 利用分类器进行命令的编码
                cc1.classify(params[0],language);
                // show execution confirmation dialog fragment 确定窗口 并执行回调函数，如果确定，那么就进行任务执行
                showDialog(findViewById(android.R.id.content));

                result = "Did classify";
            } else {
                result = "Not classify";
            }
            return result;
        }

        private void onPostExecute(String result) {
            // Handle the result on the main thread (if needed)
            // For example, update UI or log the result
            System.out.println(result);
        }

        // Clean up resources when no longer needed
        public void shutdown() {
            executorService.shutdown();
        }
    }

    /**
     * Prepare Encoded String
     * 预处理命令编码
     */
    private ArrayList<Integer> mEncodedStr;

    private void preCheck(ArrayList<Integer> encoded_string, String google_map_string) {
        // Get first and see if it is adnvacce mission
        if (encoded_string.get(0) == 107) {
            searchPlace(google_map_string);
        } else {
            callExecution(encoded_string);
        }
    }

    private void writeRecogRecord(boolean pos, String s2tStr, String encodedStr, String classifiedStr) {
        String group = "neg";
        if (pos) {
            group = "pos";
        }
//        String key = mDBRecog.child(group).push().getKey();
//        mDBRecog.child(group).child(key).child("s2tStr").setValue(s2tStr);
//        mDBRecog.child(group).child(key).child("classifiedStr").setValue(classifiedStr);
//        mDBRecog.child(group).child(key).child("encodedStr").setValue(encodedStr);
    }
    /**
     * END of Prepare Encoded String
     */

    /**
     * Confirmation box
     */
    // 显示命令确认窗口
    public void showDialog(View v) {
        // create FragmentManager and CommandConfirmationDialogFragment
        FragmentManager manager = getSupportFragmentManager();
        CommandConfirmationDialogFragment myDialogFragment = new CommandConfirmationDialogFragment();
        // send encoded_string and command into pop up window
        Bundle bundle = new Bundle();

        bundle.putString("encoded_string", cc1.getEncodedString().toString());
        bundle.putString("command", cc1.getCommand());
        myDialogFragment.setArguments(bundle);
        // show pop up window
        Log.d(TAG, "Showing dialog...");
        if (manager.findFragmentByTag("MyDialogFragment") == null) {
            runOnUiThread(() -> myDialogFragment.show(manager, "MyDialogFragment"));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_control, container, false);
    }

    @Override
    public void onDialogMessage(boolean message) {
        if (message) {
            writeRecogRecord(true, mStrIntention, cc1.getEncodedString().toString(), cc1.getCommand());
//            showFpvToast("Start executing command");
            preCheck(cc1.getEncodedString(), cc1.getGoogleMapSearchString()); // Start execution 开始执行操作
        } else {
            writeRecogRecord(false, mStrIntention, cc1.getEncodedString().toString(), cc1.getCommand());
            showcontrolToast("Command cancelled");
        }
    }


    //endregion

    //region 飞行控制器

    private void initDrone() {
        mCI.initFlightController();
        if (mCI.mFlightController != null) {
            mCI.setPhotoMode();
//            showFpvToast("Set up call bacsk");

            mCI.mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    double mDroneLocationLat = flightControllerState.getAircraftLocation().getLatitude();
                    double mDroneLocationLng = flightControllerState.getAircraftLocation().getLongitude();
                    mDroneLocation = new LatLng(mDroneLocationLat, mDroneLocationLng);
                    mDroneHeading = mCI.mFlightController.getCompass().getHeading();
//                    updateDroneLocation();
                    // set flight data
                    mAltitudeData = (double) flightControllerState.getAircraftLocation().getAltitude(); // - initAltitude;
//                    if (mAltitudeData < 18) {
//                        mAltitudeData = (double) flightControllerState.getUltrasonicHeightInMeters();
//                    }
                    mhs = Math.sqrt(flightControllerState.getVelocityX() * flightControllerState.getVelocityX()
                            + flightControllerState.getVelocityY() * flightControllerState.getVelocityY());
                    mvs = -1 * flightControllerState.getVelocityZ();
                    mdistToHome = Utils.calcDistance(mUserLocation.latitude, mUserLocation.longitude, mDroneLocation.latitude, mDroneLocation.longitude);
                    updateFlightData();
                }
            });

//            mTest.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    mCI.shootPhoto();
//                }
//            });

            // set up battery
            mCI.aircraft.getBattery().setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    mBatteryPercent = batteryState.getChargeRemainingInPercent();
                    mBatteryView.setProgress(mBatteryPercent);
                    updateBatteryStatus();
                }
            });
        }

    }

    private void updateConnection() {
//        boolean ret = false;
        BaseProduct product = DJISampleApplication.getProductInstance();
        if (product != null) {
            if (product.isConnected()) {
                //The product is connected
                showToast(DJISampleApplication.getProductInstance().getModel() + " Connected");
//                ret = true;
            } else {
                if (product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) product;
                    if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        showToast("only RC Connected");
//                        ret = true;
                    }
                }
            }
        }

//        if (!ret) {
//            // The product or the remote controller are not connected.
//            showFpvToast("Disconnected");
//        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateConnection();
            if (mCI.mFlightController == null) {
                initDrone();
            }
        }
    };

    /**
     * Update drone's distance, altitude, vertical speed, and horizontal speed
     * 更新无人机的距离，经纬度，竖直速度，水平速度
     */
    private void updateFlightData() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDistance.setText("D: " + new DecimalFormat("####").format(mdistToHome) + "m");
                mAltitude.setText("H: " + new DecimalFormat("###.#").format(mAltitudeData) + "m");
                mVerSpeed.setText("V.S: " + new DecimalFormat("##.#").format(mvs) + "m/s");
                mHorSpeed.setText("H.S: " + new DecimalFormat("##.#").format(mhs) + "m/s");
            }
        });
    }

    /**
     * Update battery level
     * 更新电池状态
     */
    private void updateBatteryStatus() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBatteryData.setText(Integer.toString(mBatteryPercent) + "%");
            }
        });
    }

    /**
     * 执行虚拟摇杆控制器
     */
    private void callExecution(ArrayList<Integer> encoded_string) {
        mEncodedStr = encoded_string;
        boolean success = false;
        if (mCI != null) {
            mCI.initFlightController();
        }
        if (mCI.mFlightController != null) {
            mCI.executeCmd(mEncodedStr);
            success = true;
        }
        if (success) {
            showToast("Instruction Sent");
        } else {
            showToast("Flight Control Error");
        }
    }

    /**
     * Search for place and sort the result by distance to the drone
     */
    private List<Address> addressList = null;
    private LatLng[] locList;
    private boolean addressList_flag = true;

    public void searchPlace(String locationName) {
        Geocoder mGeocoder = new Geocoder(mContext);
        int maxResults = 20;
        double lowerLeftLatitude = mDroneLocation.latitude - 0.05;
        double lowerLeftLongitude = mDroneLocation.longitude - 0.05;
        double upperRightLatitude = mDroneLocation.latitude + 0.05;
        double upperRightLongitude = mDroneLocation.longitude + 0.05;
//        double lowerLeftLatitude = mUserLocation.latitude - 0.05;
//        double lowerLeftLongitude = mUserLocation.longitude - 0.05;
//        double upperRightLatitude = mUserLocation.latitude + 0.05;
//        double upperRightLongitude = mUserLocation.longitude + 0.05;
        try {
            addressList = mGeocoder.getFromLocationName(locationName, maxResults, lowerLeftLatitude, lowerLeftLongitude, upperRightLatitude, upperRightLongitude);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            addressList_flag = false;
        }
        if (addressList_flag && addressList.size() != 0) {
            Bundle args = new Bundle();
            String[] places = new String[addressList.size()];
            double[] dist = new double[addressList.size()];
            LatLng[] cdArray = new LatLng[addressList.size()];
            for (int i = 0; i < addressList.size(); i++) {
                String sb = "";
                for (int k = 0; k < addressList.get(i).getMaxAddressLineIndex(); k++) {
                    sb += addressList.get(i).getAddressLine(k);
                    sb += "; ";
                }
                double lat = addressList.get(i).getLatitude();
                double lon = addressList.get(i).getLongitude();
                LatLng currentCd = new LatLng(lat, lon);
                double distance = Utils.calcDistance(mDroneLocation.latitude, mDroneLocation.longitude, lat, lon);
                sb += new DecimalFormat("####").format(distance) + "m";
                places[i] = sb;
                dist[i] = distance;
                cdArray[i] = currentCd;
                for (int j = i - 1; j >= 0; j--) {
                    if (dist[j + 1] < dist[j]) {
                        double t1 = dist[j];
                        String t2 = places[j];
                        LatLng t3 = cdArray[j];
                        dist[j] = dist[j + 1];
                        places[j] = places[j + 1];
                        cdArray[j] = cdArray[j + 1];
                        dist[j + 1] = t1;
                        places[j + 1] = t2;
                        cdArray[j + 1] = t3;
                    }
                }
            }
            locList = cdArray;
            args.putStringArray("places", places);
            mPlaceListFragment = new PlaceListFragment();
            mPlaceListFragment.setArguments(args);
            Log.e(TAG, mPlaceListFragment.getArguments().toString());
            getSupportFragmentManager().beginTransaction().add(R.id.main_layout, mPlaceListFragment).commit();
        } else {
            showToast("No result available");
            Log.e(TAG, "No result available");
            addressList_flag = true;
        }
    }

    /**
     * Get a specific place's coordinates, encode it, and call (fly to) execution
     */
    public void getPlaceCoordinates(int index) {
        getSupportFragmentManager().beginTransaction().remove(mPlaceListFragment).commit();
        double lat = locList[index].latitude;
        double lon = locList[index].longitude;
        LatLng targetLatLng = new LatLng(lat, lon);
//        showFpvToast(targetLatLng.toString());
        addressList = null;
        locList = null;

        int latInt = (int) lat;
        int latDeci = (int) ((lat - latInt) * 100000);
        int lonInt = (int) lon;
        int lonDeci = (int) ((lon - lonInt) * 100000);

        ArrayList<Integer> temp = cc1.getEncodedString();
        temp.add(latInt);
        temp.add(latDeci);
        temp.add(lonInt);
        temp.add(lonDeci);

//        showFpvToast(temp.toString());

        callExecution(temp);
    }

    //endregion


    //region UI布局
    //初始化布局，请求权限
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
        /**
         * When starting SDK services, an instance of interface DJISDKManager.DJISDKManagerCallback will be used to listen to
         * the SDK Registration result and the product changing.
         */
        mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {

            //Listens to the SDK registration result
            @Override
            public void onRegister(DJIError error) {

                if(error == DJISDKError.REGISTRATION_SUCCESS) {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Register Success", Toast.LENGTH_LONG).show();
                        }
                    });

                    DJISDKManager.getInstance().startConnectionToProduct();

                } else {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Register sdk fails, check network is available", Toast.LENGTH_LONG).show();
                        }
                    });

                }
                Log.e("TAG", error.toString());
            }

            @Override
            public void onProductDisconnect() {
                Log.d("TAG", "onProductDisconnect");
                notifyStatusChange();
            }
            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                Log.d("TAG", String.format("onProductConnect newProduct:%s", baseProduct));
                notifyStatusChange();

            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {

            }

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                          BaseComponent newComponent) {
                if (newComponent != null) {
                    newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                        @Override
                        public void onConnectivityChange(boolean isConnected) {
                            Log.d("TAG", "onComponentConnectivityChanged: " + isConnected);
                            notifyStatusChange();
                        }
                    });
                }

                Log.d("TAG",
                        String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                componentKey,
                                oldComponent,
                                newComponent));

            }
            @Override
            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

            }

            @Override
            public void onDatabaseDownloadProgress(long l, long l1) {

            }


        };
        checkAndRequestPermissions();
        SpeechUtility.createUtility(this, SpeechConstant.APPID +"=12cecf5e");
        setContentView(R.layout.activity_control);


        //初始化科大讯飞语音识别
        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);
        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = new RecognizerDialog(ControlActivity.this, mInitListener);

        initUI();
        lanBtnListener();
        cc1 = new CommandClassifier();
        // 初始化控制器
        mCI = CommandInterpreter.getUniqueInstance(mContext);
        initDrone();

        //注册接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISampleApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        Log.e(TAG, "onCreate");

//        // Register the broadcast receiver for receiving the device connection's changes.
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(DJISampleApplication.FLAG_CONNECTION_CHANGE);
//        registerReceiver(mReceiver, filter);
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }
    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }
    private Runnable updateRunnable = new Runnable() {

        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            getApplicationContext().sendBroadcast(intent);
        }
    };
    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast( "registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showToast("Register Success");
                            } else {
                                showToast( "Register sdk fails, check network is available");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            showToast("Product Disconnected");

                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                            showToast("Product Connected");

                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {

                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                                      BaseComponent newComponent) {

                            if (newComponent != null) {
                                newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                                    @Override
                                    public void onConnectivityChange(boolean isConnected) {
                                        Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
                                    }
                                });
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));

                        }
                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }

                        @Override
                        public void onDatabaseDownloadProgress(long l, long l1) {

                        }
                    });
                }
            });
        }
    }

//    public void showToast(final String msg) {
//        runOnUiThread(new Runnable() {
//            public void run() {
//                Toast.makeText(ControlActivity.this, msg, Toast.LENGTH_SHORT).show();
//            }
//        });
//    }

    private void updateTitleBar() {
        if(mConnectStatusTextView == null) return;
        boolean ret = false;
        BaseProduct product = DJISampleApplication.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(DJISampleApplication.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if(product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    //更新状态，初始化飞控，登录账户
    private void lanBtnListener() {
        mBtnLanguage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (languageType) {
                    languageType = false;
                    language = "zh_cn";
                    mBtnLanguage.setText("中文");
                } else {
                    languageType = true;
                    language = "en_us";
                    mBtnLanguage.setText("英文");
                }
            }
        });
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        updateTitleBar();
        initFlightController();
        loginAccount();

    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        if (null != mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask.cancel();
            mSendVirtualStickDataTask = null;
            mSendVirtualStickDataTimer.cancel();
            mSendVirtualStickDataTimer.purge();
            mSendVirtualStickDataTimer = null;
        }
        super.onDestroy();
    }

    //登录账户
    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }
    //初始化飞控
    private void initFlightController() {
        //实例化飞控
        Aircraft aircraft = DJISampleApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            showToast("Disconnected");
            mFlightController = null;
            return;
        } else {
            //初始化设置飞控模式
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            mFlightController.getSimulator().setStateCallback(new SimulatorState.Callback() {
                //显示状态数据
                @Override
                public void onUpdate(final SimulatorState stateData) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {

                            String yaw = String.format("%.2f", stateData.getYaw());
                            String pitch = String.format("%.2f", stateData.getPitch());
                            String roll = String.format("%.2f", stateData.getRoll());
                            String positionX = String.format("%.2f", stateData.getPositionX());
                            String positionY = String.format("%.2f", stateData.getPositionY());
                            String positionZ = String.format("%.2f", stateData.getPositionZ());

                            mTextView.setText("Yaw : " + yaw + ", Pitch : " + pitch + ", Roll : " + roll + "\n" + ", PosX : " + positionX +
                                    ", PosY : " + positionY +
                                    ", PosZ : " + positionZ);
                        }
                    });
                }
            });
        }
    }

    private void initUI() {

        mBtnEnableVirtualStick = (Button) findViewById(R.id.btn_enable_virtual_stick);
        mBtnDisableVirtualStick = (Button) findViewById(R.id.btn_disable_virtual_stick);
        mBtnTakeOff = (Button) findViewById(R.id.btn_take_off);
        mBtnLand = (Button) findViewById(R.id.btn_land);
        mBtnSimulator = (ToggleButton) findViewById(R.id.btn_start_simulator);
        mTextView = (TextView) findViewById(R.id.textview_simulator);
        mBtnSub = (Button) findViewById(R.id.sub_btn);
        mCMD = (EditText) findViewById(R.id.cmd_input);
        mBtnPhoto = (Button) findViewById(R.id.btn_photo);
        mBtnDownload = (Button) findViewById(R.id.btn_to_download);
        mBtnWaypoint = (Button) findViewById(R.id.btn_waypoint);
        mBtnLanguage = (Button) findViewById(R.id.sub_lan);
        mBtnLanguage2 = (Button) findViewById(R.id.btn_control_lan);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
        mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);
        mBatteryView = (BatteryView) findViewById(R.id.battery_view);
        mBatteryData = (TextView) findViewById(R.id.battery_data);
        mAltitude = (TextView) findViewById(R.id.Altitude);
        mVerSpeed = (TextView) findViewById(R.id.VerticalSpeed);
        mDistance = (TextView) findViewById(R.id.Distance);
        mHorSpeed = (TextView) findViewById(R.id.HorizonSpeed);
        logTextView = findViewById(R.id.LogView);

        mBtnEnableVirtualStick.setOnClickListener(this);
        mBtnDisableVirtualStick.setOnClickListener(this);
        mBtnTakeOff.setOnClickListener(this);
        mBtnLand.setOnClickListener(this);
        mBtnSub.setOnClickListener(this);
        mBtnPhoto.setOnClickListener(this);
        mBtnDownload.setOnClickListener(this);
        mBtnWaypoint.setOnClickListener(this);

        findViewById(R.id.btn_control_recognize).setOnClickListener(this);
        findViewById(R.id.btn_control_stop).setOnClickListener(this);
        findViewById(R.id.btn_control_cancel).setOnClickListener(this);
        findViewById(R.id.btn_control_lan).setOnClickListener(this);

        mBtnSimulator.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {

                    mTextView.setVisibility(View.VISIBLE);

                    if (mFlightController != null) {

                        mFlightController.getSimulator()
                                .start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10),
                                        new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError != null) {
                                            showToast(djiError.getDescription());
                                        }else
                                        {
                                            showToast("Start Simulator Success");
                                        }
                                    }
                                });
                    }

                } else {

                    mTextView.setVisibility(View.INVISIBLE);

                    if (mFlightController != null) {
                        mFlightController.getSimulator()
                                .stop(new CommonCallbacks.CompletionCallback() {
                                            @Override
                                            public void onResult(DJIError djiError) {
                                                if (djiError != null) {
                                                    showToast(djiError.getDescription());
                                                }else
                                                {
                                                    showToast("Stop Simulator Success");
                                                }
                                            }
                                        }
                                );
                    }
                }
            }
        });

        mScreenJoystickRight.setJoystickListener(new OnScreenJoystickListener(){

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }

                float pitchJoyControlMaxSpeed = 10;
                float rollJoyControlMaxSpeed = 10;

                mPitch = (float)(pitchJoyControlMaxSpeed * pX);

                mRoll = (float)(rollJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
                }

            }

        });

        mScreenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }
                float verticalJoyControlMaxSpeed = 2;
                float yawJoyControlMaxSpeed = 30;

                mYaw = (float)(yawJoyControlMaxSpeed * pX);
                mThrottle = (float)(verticalJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
                }

            }
        });
        lanBtnListener2();
    }

    private void updateLog(String message) {
        runOnUiThread(() -> {
            // 将新的日志追加到 TextView 中
            String currentLog = logTextView.getText().toString();
            logTextView.setText(currentLog + "\n" + message);

        });
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btn_enable_virtual_stick:
                if (mFlightController != null){

                    mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null){
                                showToast(djiError.getDescription());
                            }else
                            {
                                showToast("Enable Virtual Stick Success");
                            }
                        }
                    });

                }
                break;

            case R.id.btn_disable_virtual_stick:

                if (mFlightController != null){
                    mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                showToast("Disable Virtual Stick Success");
                            }
                        }
                    });
                }
                break;

            case R.id.btn_take_off:
                if (mFlightController != null){
                    mFlightController.startTakeoff(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        showToast(djiError.getDescription());
                                    } else {
                                        showToast("Take off Success");
                                    }
                                }
                            }
                    );
                }

                break;

            case R.id.btn_land:
                if (mFlightController != null){

                    mFlightController.startLanding(
                            djiError -> {
                                if (djiError != null) {
                                    showToast(djiError.getDescription());
                                } else {
                                    showToast("Start Landing");
                                }
                            }
                    );

                }

                break;

            case R.id.sub_btn:
                mStrIntention = mCMD.getText().toString();
                // Tokenize command_in_text
                StringTokenizer st = new StringTokenizer(mStrIntention);
                ArrayList<String> tokenedCommand = new ArrayList<>();
                while (st.hasMoreTokens()) {
                    tokenedCommand.add(st.nextToken());
                }
                // Replace mavic similar words
//                    tokenedCommand = findMavicSimilar(tokenedCommand);
                // Change arraylist to string
                mStrIntention = TextUtils.join(" ", tokenedCommand);
                // Execute NLC
                ClassificationTask cft = new ClassificationTask();
                cft.execute(tokenedCommand);
                break;
            case R.id.btn_photo:
                Intent intent2 = new Intent(v.getContext(), VideoActivity.class);
                v.getContext().startActivity(intent2);
                break;
            case R.id.btn_to_download:
                Intent intent3 = new Intent(v.getContext(), DownloadActivity.class);
                v.getContext().startActivity(intent3);
                break;
            case R.id.btn_waypoint:
                Intent intent4 = new Intent(v.getContext(), Waypoint2Activity.class);
                v.getContext().startActivity(intent4);
                break;
            case R.id.btn_control_recognize:
                buffer.setLength(0);//长度清空
                mIatResults.clear();//清除存贮结果
                // 设置参数
                setParam();
                if (dialogType == 0) {
                    // 显示听写对话框
                    mIatDialog.setListener(mRecognizerDialogListener);
                    mIatDialog.show();
                    showToast("开始听写");
                } else if (dialogType == 1) {
                    // 不显示听写对话框
                    resultCode = mIat.startListening(mRecognizerListener);
                    if (resultCode != ErrorCode.SUCCESS) {
                        showToast("听写失败,错误码：" + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                    } else {
                        showToast("开始听写");
                    }
                } else if (dialogType == 2) {
                    // 自定义听写对话框
                    showAlertDialog();
                    resultCode = mIat.startListening(mRecognizerListener);
                    if (resultCode != ErrorCode.SUCCESS) {
                        showToast("听写失败,错误码：" + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                    } else {
                        showToast("开始听写");
                    }
                }
                break;
//            case R.id.languageText:
//                if (languageType) {
//                    languageType = false;
//                    language = "zh_cn";
//                    languageText.setText("点击切换语种：中文");
//                } else {
//                    languageType = true;
//                    language = "en_us";
//                    languageText.setText("点击切换语种：英文");
//                }
//                mIat.setParameter(SpeechConstant.LANGUAGE, language);
//                break;
            // 停止听写
            case R.id.btn_control_stop:
                mIat.stopListening();
                showToast("停止听写");
                break;
            // 取消听写
            case R.id.btn_control_cancel:
                mIat.cancel();
                showToast("取消听写");
                break;

            default:
                break;
        }
    }

    class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {

            if (mFlightController != null) {
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), djiError -> {

                        }
                );
            }
        }
    }

    private void lanBtnListener2() {
        mBtnLanguage2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (languageType) {
                    languageType = false;
                    language = "zh_cn";
                    mBtnLanguage2.setText("中文");
                } else {
                    languageType = true;
                    language = "en_us";
                    mBtnLanguage2.setText("英文");
                }
                mIat.setParameter(SpeechConstant.LANGUAGE, language);
            }
        });
    }

    public void showcontrolToast(final String msg) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mContext.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    //endregion


}
