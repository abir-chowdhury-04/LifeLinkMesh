package com.abir2307055.lifelinkmesh.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.R;
import com.abir2307055.lifelinkmesh.model.Peer;

import java.util.ArrayList;
import java.util.List;

public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.PeerViewHolder> {

    private final List<Peer> peers = new ArrayList<>();

    // Adds one peer and tells RecyclerView only that row changed.
    public void addPeer(Peer peer) {
        peers.add(peer);
        notifyItemInserted(peers.size() - 1);
    }

    // Holds the views of ONE row so they can be reused.
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
        // Use the Peer object, not the position number: positions can shift
        // when the list changes.
        holder.itemView.setOnClickListener(v ->
                Toast.makeText(v.getContext(),
                        "Clicked " + peer.getName(),
                        Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }
}