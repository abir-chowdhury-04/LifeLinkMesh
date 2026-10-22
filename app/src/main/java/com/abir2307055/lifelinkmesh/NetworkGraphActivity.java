package com.abir2307055.lifelinkmesh;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.abir2307055.lifelinkmesh.mesh.MeshHub;
import com.abir2307055.lifelinkmesh.mesh.NetworkGraph;
import com.abir2307055.lifelinkmesh.ui.GraphView;

public class NetworkGraphActivity extends AppCompatActivity {

    private MeshHub hub;
    private GraphView graphView;
    private TextView txtGraphInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Redraws twice a second, so highlights fade and new links appear by themselves.
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        hub = MeshHub.getInstance(this);
        graphView = findViewById(R.id.graphView);
        txtGraphInfo = findViewById(R.id.txtGraphInfo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);   // stop redrawing when the screen is not visible
    }

    private void refresh() {
        NetworkGraph.Snapshot snapshot = hub.getGraphSnapshot();
        graphView.setSnapshot(snapshot);
        txtGraphInfo.setText(snapshot.nodes.size() + " device(s), "
                + snapshot.edges.size() + " link(s)   ·   orange = a message just used this link");
    }
}