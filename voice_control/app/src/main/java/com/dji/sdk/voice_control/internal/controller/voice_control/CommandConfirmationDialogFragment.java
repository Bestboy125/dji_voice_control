package com.dji.sdk.voice_control.internal.controller.voice_control;

import android.app.Activity;
//import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.DialogFragment;

import com.dji.sdk.voice_control.R;
/**
 * Confirmation box
 */

public class CommandConfirmationDialogFragment extends DialogFragment implements View.OnClickListener{
    Button yes, no;
    TextView display;
    Communicator communicator;
    String encoded_string, command;

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        communicator = (Communicator) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        encoded_string = getArguments().getString("encoded_string");
        command = getArguments().getString("command");

        View view = inflater.inflate(R.layout.confirm_command_dialog, null);
        display = (TextView) view.findViewById(R.id.textConfirmView);
        yes = (Button) view.findViewById(R.id.yes);
        no = (Button) view.findViewById(R.id.no);
        display.setText(command + '\n'+ encoded_string);
        yes.setOnClickListener(this);
        no.setOnClickListener(this);
        setCancelable(false); // make sure user cannot cancel this window
        return view;
    }

    @Override
    public void onClick(View view){
        if(view.getId()==R.id.yes){
            communicator.onDialogMessage(true);
            dismiss();
        }
        else{
            communicator.onDialogMessage(false);
            dismiss();
        }
    }

    public void show(FragmentManager manager, String myDialogFragment) {
        super.show(manager, myDialogFragment);
    }

    public interface Communicator{
        View onCreateView(LayoutInflater inflater, ViewGroup container,
                          Bundle savedInstanceState);

        public void onDialogMessage(boolean message);
    }
}
