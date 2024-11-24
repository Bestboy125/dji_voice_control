package com.dji.sdk.voice_control.internal.controller;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.voice_control.CommandClassifier;
import com.dji.sdk.voice_control.internal.controller.voice_control.CommandConfirmationDialogFragment;
import com.dji.sdk.voice_control.internal.controller.voice_control.PlaceListFragment;
import com.dji.sdk.voice_control.internal.controller.voice_control.VoiceControlActivity;
import com.dji.sdk.voice_control.internal.controller.waypoint.Waypoint2Activity;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.iflytek.cloud.SpeechConstant;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
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

public class ControlActivity extends AppCompatActivity implements View.OnClickListener,CommandConfirmationDialogFragment.Communicator {
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
    private Button mBtnSpeak;
    private Button mBtnSub;
    private EditText mCMD;
    private CommandClassifier cc1;
    private Button mBtnPhoto;
    private Button mBtnDownload;
    private Button mBtnWaypoint;
    private Button mBtnLanguage;
    private boolean languageType;
    private String language="en_us";

    private TextView mTextView;
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
        setContentView(R.layout.activity_control);

        initUI();
        lanBtnListener();
        cc1 = new CommandClassifier();

        // 初始化控制器
        mCI = CommandInterpreter.getUniqueInstance(mContext);
        initFlightController();

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

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
        }
    };

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(ControlActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

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
        mBtnSpeak = (Button) findViewById(R.id.btn_speak);
        mTextView = (TextView) findViewById(R.id.textview_simulator);
        mBtnSub = (Button) findViewById(R.id.sub_btn);
        mCMD = (EditText) findViewById(R.id.cmd_input);
        mBtnPhoto = (Button) findViewById(R.id.btn_photo);
        mBtnDownload = (Button) findViewById(R.id.btn_to_download);
        mBtnWaypoint = (Button) findViewById(R.id.btn_waypoint);
        mBtnLanguage = (Button) findViewById(R.id.sub_lan);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
        mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);

        mBtnEnableVirtualStick.setOnClickListener(this);
        mBtnDisableVirtualStick.setOnClickListener(this);
        mBtnTakeOff.setOnClickListener(this);
        mBtnLand.setOnClickListener(this);
        mBtnSpeak.setOnClickListener(this);
        mBtnSub.setOnClickListener(this);
        mBtnPhoto.setOnClickListener(this);
        mBtnDownload.setOnClickListener(this);
        mBtnWaypoint.setOnClickListener(this);

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

            case R.id.btn_speak:
                Intent intent1 = new Intent(v.getContext(), VoiceControlActivity.class);
                v.getContext().startActivity(intent1);
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

    //region 飞行控制
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
            showcontrolToast("Instruction Sent");
        } else {
            showcontrolToast("Flight Control Error");
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
            showcontrolToast("No result available");
            Log.e(TAG, "No result available");
            addressList_flag = true;
        }
    }
    //endregion

    public void showcontrolToast(final String msg) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mContext.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }


}
