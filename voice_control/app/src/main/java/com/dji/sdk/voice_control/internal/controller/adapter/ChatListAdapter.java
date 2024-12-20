package com.dji.sdk.voice_control.internal.controller.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.chatgpt.ChatMessage;
import com.dji.sdk.voice_control.internal.controller.chatgpt.ChatMessageData;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.utils.ClipboardUtil;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.MyViewHolder> {

    public ChatListAdapter() {
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemLayout = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, null);
        MyViewHolder myViewHolder = new MyViewHolder(itemLayout);
        return myViewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        ChatMessage chatMessage = ChatMessageData.getInstance().getChatMessage(position);
        String msg = chatMessage.getMsg();
        String owner = chatMessage.getOwner();
        Bitmap image = chatMessage.getImage();

        if (owner.equals(Constant.OWNER_BOT)) {
            holder.mPbThink.setVisibility(View.GONE);
            holder.mRlHuman.setVisibility(View.GONE);
            holder.mRlBot.setVisibility(View.VISIBLE);
            holder.mTvMsgBot.setText(msg);

            // TODO 复制去掉
            holder.mIvCopy.setVisibility(View.GONE);
            holder.mIvCopy.setOnClickListener(v -> {
                // 复制到剪贴板
                if (ClipboardUtil.copy(msg)) {
                    Toast.makeText(v.getContext(), "已复制", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(v.getContext(), "复制出错", Toast.LENGTH_SHORT).show();
                }
            });

        } else if (owner.equals(Constant.OWNER_HUMAN)) {
            holder.mPbThink.setVisibility(View.GONE);
            holder.mRlBot.setVisibility(View.GONE);
            holder.mRlHuman.setVisibility(View.VISIBLE);
            holder.mTvMsgHuman.setText(msg);
            // 显示用户的图像消息
            if (image != null) {
                Log.d("ImageDebug", "Bitmap size: " + image.getWidth() + "x" + image.getHeight());
                holder.mTvMsgHuman.setVisibility(View.GONE);
                holder.mIvImage.setVisibility(View.VISIBLE);// 假设你在布局中添加了 ImageView 用于显示图片
                // 设置图片大小为200x100
                // 设置 ImageView 的大小为 Bitmap 的宽高
                holder.mIvImage.getLayoutParams().width = image.getWidth();
                holder.mIvImage.getLayoutParams().height = image.getHeight();
                holder.mIvImage.requestLayout();
                // 使用 Glide 加载图片
                holder.mIvImage.setImageBitmap(null);
                Glide.with(holder.mIvImage.getContext())
                        .load(image)  // 传入 Bitmap 对象
                        .into(holder.mIvImage);
            } else {
                holder.mIvImage.setVisibility(View.GONE);
                holder.mTvMsgHuman.setVisibility(View.VISIBLE);
            }
            holder.mIvCopy.setVisibility(View.GONE);
        } else if (owner.equals(Constant.OWNER_BOT_THINK)) {
            holder.mPbThink.setVisibility(View.VISIBLE);
            holder.mRlHuman.setVisibility(View.GONE);
            holder.mRlBot.setVisibility(View.VISIBLE);
            holder.mTvMsgBot.setText(msg);
            holder.mIvCopy.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return ChatMessageData.getInstance().getSize();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        private final RelativeLayout mRlBot;
        private final RelativeLayout mRlHuman;
        private TextView mTvMsgBot;
        private TextView mTvMsgHuman;
        private ProgressBar mPbThink;
        private ImageView mIvCopy;
        private ImageView mIvImage;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            mRlBot = itemView.findViewById(R.id.rl_bot);
            mRlHuman = itemView.findViewById(R.id.rl_human);

            mTvMsgBot = itemView.findViewById(R.id.tv_msg_bot);
            mTvMsgHuman = itemView.findViewById(R.id.tv_msg_human);

            mPbThink = itemView.findViewById(R.id.pb_think);
            mIvCopy = itemView.findViewById(R.id.iv_icon_copy);
            mIvImage = itemView.findViewById(R.id.iv_image);
        }
    }

}
