package com.abir2307055.lifelinkmesh.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.R;
import com.abir2307055.lifelinkmesh.model.Peer;

import java.util.ArrayList;
import java.util.List;

public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.PeerViewHolder> {

    public interface OnPeerClickListener {
        void onPeerClick(Peer peer);
    }

    private final List<Peer> peers = new ArrayList<>();
    private OnPeerClickListener clickListener;

    public void setOnPeerClickListener(OnPeerClickListener listener) {
        this.clickListener = listener;
    }

    private int indexOf(String endpointId) {
        for (int i = 0; i < peers.size(); i++) {
            if (peers.get(i).getEndpointId().equals(endpointId)) {
                return i;
            }
        }
        return -1;
    }

    // Adds the peer only if we don't already have that endpoint.
    public void addIfAbsent(Peer peer) {
        if (indexOf(peer.getEndpointId()) >= 0) {
            return;
        }
        peers.add(peer);
        notifyItemInserted(peers.size() - 1);
    }

    // Changes one peer's status. Returns false if the peer is not in the list.
    public boolean updateStatus(String endpointId, String status) {
        int index = indexOf(endpointId);
        if (index < 0) {
            return false;
        }
        peers.get(index).setStatus(status);
        notifyItemChanged(index);
        return true;
    }

    // A peer that went out of range before we connected is removed.
    public void removeIfDiscovered(String endpointId) {
        int index = indexOf(endpointId);
        if (index >= 0 && Peer.STATUS_DISCOVERED.equals(peers.get(index).getStatus())) {
            peers.remove(index);
            notifyItemRemoved(index);
        }
    }

    // Empties the whole list, e.g. when the mesh is stopped.
    public void clear() {
        int count = peers.size();
        peers.clear();
        notifyItemRangeRemoved(0, count);
    }

    // Replaces the whole list, e.g. when a screen is recreated.
    public void setPeers(List<Peer> newPeers) {
        peers.clear();
        peers.addAll(newPeers);
        notifyDataSetChanged();
    }

    public static class PeerViewHolder extends RecyclerView.ViewHolder {
        TextView txtPeerName;
        TextView txtPeerStatus;

        public PeerViewHolder(@NonNull View itemView) {
            super(itemView);
            txtPeerName = itemView.findViewById(R.id.txtPeerName);
            txtPeerStatus = itemView.findViewById(R.id.txtPeerStatus);
        }
    }

    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_peer, parent, false);
        return new PeerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        Peer peer = peers.get(position);
        holder.txtPeerName.setText(peer.getName());
        holder.txtPeerStatus.setText(peer.getStatus());
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPeerClick(peer);
            }
        });
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }
}