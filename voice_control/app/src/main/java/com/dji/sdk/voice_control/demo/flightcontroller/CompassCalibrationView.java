package com.dji.sdk.voice_control.demo.flightcontroller;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.voice_control.internal.view.BaseThreeBtnView;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.Compass;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;

/**
 * Class of compass calibration.
 */
public class CompassCalibrationView extends BaseThreeBtnView {

    private Compass compass;

    public CompassCalibrationView(Context context, AttributeSet attrs) {
        super(context,attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController =
                ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();

            flightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState djiFlightControllerCurrentState) {
                    if (null != compass) {
                        String description =
                            "标定状态: " + compass.getCalibrationState() + "\n"
                            + "航向角度: " + compass.getHeading() + "\n"
                            + "是否正在标定: " + compass.isCalibrating() + "\n";

                        changeDescription(description);
                    }
                }
            });
            if (ModuleVerificationUtil.isCompassAvailable()) {
                compass = flightController.getCompass();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(ModuleVerificationUtil.isFlightControllerAvailable()) {
            ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController().setStateCallback(null);
        }
    }

    @Override
    protected int getDescriptionResourceId() {
        return R.string.compass_calibration_description;
    }

    @Override
    protected void handleRightBtnClick() {
        if (ModuleVerificationUtil.isCompassAvailable()) {
            compass = ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController().getCompass();

            compass.stopCalibration(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        changeDescription("标定停止");
                    } else {
                        changeDescription("标定停止失败: " + djiError.getDescription());
                    }
                }
            });
        }
    }

    @Override
    protected void handleMiddleBtnClick() {

    }

    @Override
    protected int getMiddleBtnTextResourceId() {
        return DISABLE;
    }

    @Override
    protected int getRightBtnTextResourceId() {
        return R.string.compass_calibration_stop_calibration;
    }

    @Override
    protected int getLeftBtnTextResourceId() {
        return R.string.compass_calibration_start_calibration;
    }

    @Override
    protected void handleLeftBtnClick() {
        if (ModuleVerificationUtil.isCompassAvailable()) {
            compass = ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController().getCompass();

            compass.startCalibration(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        changeDescription("标定开始，请先水平校准，然后垂直校准");
                    } else {
                        changeDescription("标定失败： " + djiError.getDescription());
                    }
                }
            });
        }
    }

    @Override
    public int getDescription() {
        return R.string.flight_controller_listview_compass_calibration;
    }
}
