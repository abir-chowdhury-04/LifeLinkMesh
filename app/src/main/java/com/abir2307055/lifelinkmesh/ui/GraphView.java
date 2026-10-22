package com.abir2307055.lifelinkmesh.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.abir2307055.lifelinkmesh.mesh.NetworkGraph;

// A custom View: draws phones as dots and links as lines.
public class GraphView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint litPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private NetworkGraph.Snapshot snapshot;

    public GraphView(Context context) {
        this(context, null);
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        setBackgroundColor(Color.WHITE);   // fixed colours, same look in dark mode

        linePaint.setColor(Color.GRAY);
        linePaint.setStrokeWidth(3 * density);

        litPaint.setColor(Color.rgb(255, 140, 0));   // orange
        litPaint.setStrokeWidth(7 * density);

        nodePaint.setColor(Color.rgb(46, 125, 50));  // green: other phones
        mePaint.setColor(Color.rgb(21, 101, 192));   // blue: this phone

        textPaint.setColor(Color.BLACK);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));
    }

    // Called by the screen with fresh data; the view redraws itself.
    public void setSnapshot(NetworkGraph.Snapshot snapshot) {
        this.snapshot = snapshot;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (snapshot == null || snapshot.nodes.isEmpty()) {
            canvas.drawText("Nothing to show yet", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        int count = snapshot.nodes.size();
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float margin = 70 * density;
        float radius = Math.min(centerX, centerY) - margin;
        float nodeRadius = 18 * density;

        // Where each phone goes: evenly around a circle, "me" at the top.
        float[] x = new float[count];
        float[] y = new float[count];
        for (int i = 0; i < count; i++) {
            if (count == 1) {
                x[i] = centerX;
                y[i] = centerY;
            } else {
                double angle = -Math.PI / 2 + 2 * Math.PI * i / count;
                x[i] = centerX + (float) (radius * Math.cos(angle));
                y[i] = centerY + (float) (radius * Math.sin(angle));
            }
        }

        // Normal links first, then lit links on top, then the dots.
        for (int pass = 0; pass < 2; pass++) {
            for (NetworkGraph.Edge edge : snapshot.edges) {
                if (edge.lit != (pass == 1)) {
                    continue;
                }
                int from = snapshot.nodes.indexOf(edge.a);
                int to = snapshot.nodes.indexOf(edge.b);
                if (from < 0 || to < 0) {
                    continue;
                }
                canvas.drawLine(x[from], y[from], x[to], y[to],
                        edge.lit ? litPaint : linePaint);
            }
        }

        for (int i = 0; i < count; i++) {
            String name = snapshot.nodes.get(i);
            boolean isMe = name.equals(snapshot.me);
            canvas.drawCircle(x[i], y[i], nodeRadius, isMe ? mePaint : nodePaint);
            String label = isMe ? name + " (me)" : name;
            canvas.drawText(label, x[i],
                    y[i] + nodeRadius + textPaint.getTextSize() + 4 * density, textPaint);
        }
    }
}