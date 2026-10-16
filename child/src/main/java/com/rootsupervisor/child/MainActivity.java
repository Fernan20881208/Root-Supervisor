package com.rootsupervisor.child;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 101;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status;
    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (status != null) status.setText(CaptureService.getStatus());
            ui.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(22, 17, 36));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(56), dp(24), dp(24));
        scroll.addView(root);
        TextView title = label("Root Supervisor", 28, 0xffe6dbff);
        root.addView(title);
        root.addView(label("Compartir pantalla requiere autorización en este teléfono. La sesión es visible y se puede detener en cualquier momento.", 16, Color.WHITE));
        status = label("", 17, 0xffe5c6ff);
        status.setPadding(0, dp(26), 0, dp(26));
        root.addView(status);
        Button start = button("Compartir pantalla");
        start.setOnClickListener(v -> startCapture());
        root.addView(start);
        Button stop = button("Detener transmisión");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            status.setText("Deteniendo…");
        });
        root.addView(stop);
        root.addView(label("Para ver la sesión, conecta ambos teléfonos a la misma red Wi-Fi y escribe la IP y el código mostrados arriba en Root Supervisor Parent. Las imágenes viajan cifradas y no se guardan.", 14, 0xffc4bdd2));
        setContentView(scroll);
    }

    private void startCapture() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
        if (CaptureService.isActive()) {
            status.setText("Ya existe una sesión activa. Detenla antes de empezar otra.");
            return;
        }
        MediaProjectionManager mgr = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_CAPTURE && result == RESULT_OK && data != null) {
            Intent service = new Intent(this, CaptureService.class);
            service.putExtra("resultCode", result);
            service.putExtra("projectionData", data);
            startForegroundService(service);
        } else if (request == REQUEST_CAPTURE) {
            status.setText("La captura no fue autorizada.");
        }
    }

    @Override protected void onResume() {
        super.onResume(); ui.removeCallbacks(update); ui.post(update);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(update); super.onPause();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(color); t.setGravity(Gravity.START);
        t.setPadding(0, dp(10), 0, dp(10)); return t;
    }
    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff6443a7));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54));
        p.bottomMargin = dp(14); b.setLayoutParams(p); return b;
    }
}
