package com.abir2307055.lifelinkmesh.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.R;
import com.abir2307055.lifelinkmesh.data.SosAlert;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SosAdapter extends RecyclerView.Adapter<SosAdapter.SosViewHolder> {

    public interface OnSosClickListener {
        void onSosClick(SosAlert alert);
    }

    private final List<SosAlert> alerts = new ArrayList<>();
    private OnSosClickListener clickListener;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void setOnSosClickListener(OnSosClickListener listener) {
        this.clickListener = listener;
    }

    // Most urgent category first; within a category, newest first.
    private void sortAlerts() {
        Collections.sort(alerts, (a, b) -> {
            if (a.priority() != b.priority()) {
                return Integer.compare(a.priority(), b.priority());
            }
            return Long.compare(b.timestamp, a.timestamp);
        });
    }

    public void setAlerts(List<SosAlert> newAlerts) {
        alerts.clear();
        alerts.addAll(newAlerts);
        sortAlerts();
        notifyDataSetChanged();
    }

    // The new alert may land in the middle of the list, so the whole list is redrawn.
    public void addAlert(SosAlert alert) {
        alerts.add(alert);
        sortAlerts();
        notifyDataSetChanged();
    }

    public static class SosViewHolder extends RecyclerView.ViewHolder {
        TextView txtSosTitle;
        TextView txtSosNote;
        TextView txtSosLocation;

        public SosViewHolder(@NonNull View itemView) {
            super(itemView);
            txtSosTitle = itemView.findViewById(R.id.txtSosTitle);
            txtSosNote = itemView.findViewById(R.id.txtSosNote);
            txtSosLocation = itemView.findViewById(R.id.txtSosLocation);
        }
    }

    @NonNull
    @Override
    public SosViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sos, parent, false);
        return new SosViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SosViewHolder holder, int position) {
        SosAlert alert = alerts.get(position);
        String who = alert.mine ? "ME (sent)" : alert.senderName;
        holder.txtSosTitle.setText(alert.category.toUpperCase(Locale.ROOT)
                + " · " + who + " · " + timeFormat.format(new Date(alert.timestamp)));
        holder.txtSosNote.setText(
                alert.note == null || alert.note.isEmpty() ? "(no note)" : alert.note);
        if (alert.hasLocation) {
            holder.txtSosLocation.setText(String.format(Locale.US,
                    "Location: %.5f, %.5f (±%.0f m) · tap to open map",
                    alert.latitude, alert.longitude, alert.accuracy));
        } else {
            holder.txtSosLocation.setText("No location");
        }
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSosClick(alert);
            }
        });
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    public void clear() {
        int count = alerts.size();
        alerts.clear();
        notifyItemRangeRemoved(0, count);
    }
}