package com.abir2307055.lifelinkmesh.ui;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.R;
import com.abir2307055.lifelinkmesh.data.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    // Replaces the whole list (used once, when the screen first loads from Room).
    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    // Adds one message at the end and tells RecyclerView only that row is new.
    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView txtMsgHeader;
        TextView txtMsgText;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            txtMsgHeader = itemView.findViewById(R.id.txtMsgHeader);
            txtMsgText = itemView.findViewById(R.id.txtMsgText);
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        String who = message.mine ? "Me" : message.senderName;
        holder.txtMsgHeader.setText(who + " · " + timeFormat.format(new Date(message.timestamp)));
        holder.txtMsgText.setText(message.text);
        // My messages on the right, other people's on the left.
        int gravity = message.mine ? Gravity.END : Gravity.START;
        holder.txtMsgHeader.setGravity(gravity);
        holder.txtMsgText.setGravity(gravity);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void clear() {
        int count = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, count);
    }
}