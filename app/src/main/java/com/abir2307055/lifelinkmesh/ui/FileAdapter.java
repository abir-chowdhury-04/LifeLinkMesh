package com.abir2307055.lifelinkmesh.ui;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// The "received files" list.
public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    private final List<File> files = new ArrayList<>();
    private OnFileClickListener clickListener;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());

    public void setOnFileClickListener(OnFileClickListener listener) {
        this.clickListener = listener;
    }

    public void setFiles(List<File> newFiles) {
        files.clear();
        files.addAll(newFiles);
        notifyDataSetChanged();
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView txtFileName;
        TextView txtFileInfo;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            txtFileName = itemView.findViewById(R.id.txtFileName);
            txtFileInfo = itemView.findViewById(R.id.txtFileInfo);
        }
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = files.get(position);
        holder.txtFileName.setText(file.getName());
        holder.txtFileInfo.setText(
                Formatter.formatFileSize(holder.itemView.getContext(), file.length())
                        + " · " + timeFormat.format(new Date(file.lastModified())));
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onFileClick(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }
}