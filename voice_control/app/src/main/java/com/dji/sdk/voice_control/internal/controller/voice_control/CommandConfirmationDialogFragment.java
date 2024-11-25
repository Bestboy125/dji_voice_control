package com.dji.sdk.voice_control.internal.controller.voice_control;

//import android.app.DialogFragment;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

        import androidx.fragment.app.DialogFragment;

import com.dji.sdk.voice_control.R;

/**
 * Confirmation box
 */

public class CommandConfirmationDialogFragment extends DialogFragment implements View.OnClickListener {
    private Button yes, no;
    private TextView display;
    private Communicator communicator;
    private String encoded_string, command;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Communicator) {
            communicator = (Communicator) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement Communicator");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        encoded_string = getArguments().getString("encoded_string");
        command = getArguments().getString("command");

        View view = inflater.inflate(R.layout.confirm_command_dialog, container, false);
        display = view.findViewById(R.id.textConfirmView);
        yes = view.findViewById(R.id.yes);
        no = view.findViewById(R.id.no);

        display.setText(command + '\n' + encoded_string);
        yes.setOnClickListener(this);
        no.setOnClickListener(this);
        display.setTextIsSelectable(true);

        display.setOnLongClickListener(view1 -> {
            int start = display.getSelectionStart();
            int end = display.getSelectionEnd();
            if (start >= 0 && end >= 0 && start != end) {
                String selectedText = display.getText().subSequence(start, end).toString();

                // 弹出编辑对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                EditText editText = new EditText(getContext());
                editText.setText(selectedText);
                builder.setTitle("编辑文本")
                        .setView(editText)
                        .setPositiveButton("确定", (dialog, which) -> {
                            String newText = editText.getText().toString();
                            SpannableStringBuilder spannable = new SpannableStringBuilder(display.getText());
                            spannable.replace(start, end, newText);
                            display.setText(spannable);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
            return true;
        });

        setCancelable(false);
        return view;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.yes) {
            communicator.onDialogMessage(true);
        } else {
            communicator.onDialogMessage(false);
        }
        dismiss();
    }

    public interface Communicator {
        View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);

        void onDialogMessage(boolean message);
    }
}
