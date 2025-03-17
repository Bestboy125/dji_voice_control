package com.dji.sdk.voice_control.internal.controller.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.track.DetectedObject;

import java.util.List;

public class DetectedObjectsAdapter extends RecyclerView.Adapter<DetectedObjectsAdapter.ViewHolder> {

    public interface OnItemSelectedListener {
        void onItemSelected(int position);
    }

    private List<DetectedObject> detectedObjects;
    private OnItemSelectedListener listener;

    public DetectedObjectsAdapter(List<DetectedObject> detectedObjects, OnItemSelectedListener listener) {
        this.detectedObjects = detectedObjects;
        this.listener = listener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCropped;
        TextView txtId, txtClass, txtConf;
        CheckBox checkBox;

        public ViewHolder(View itemView) {
            super(itemView);
            imgCropped = itemView.findViewById(R.id.imgCropped);
            txtId = itemView.findViewById(R.id.txtId);
            txtClass = itemView.findViewById(R.id.txtClass);
            txtConf = itemView.findViewById(R.id.txtConf);
            checkBox = itemView.findViewById(R.id.checkBox);
        }
    }

    @Override
    public DetectedObjectsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detected_object, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DetectedObjectsAdapter.ViewHolder holder, int position) {
        DetectedObject obj = detectedObjects.get(position);
        holder.imgCropped.setImageBitmap(obj.getCroppedImage());
        holder.txtId.setText("ID: " + obj.getId());
        holder.txtClass.setText("Class: " + obj.getClassName());
        holder.txtConf.setText(String.format("Conf: %.3f", obj.getConf()));

        // 防止复用导致的勾选框状态错乱
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(obj.isSelected());

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onItemSelected(holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return detectedObjects.size();
    }

    // 获取被选中的对象 ID
    public String getSelectedId() {
        for (DetectedObject obj : detectedObjects) {
            if (obj.isSelected()) {
                return obj.getId();
            }
        }
        return null;
    }
}
