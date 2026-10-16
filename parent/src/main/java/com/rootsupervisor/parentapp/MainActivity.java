package com.rootsupervisor.parentapp;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class MainActivity extends Activity {
    private EditText ip, code;
    private ImageView preview;
    private TextView status;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SecureRandom rng = new SecureRandom();
    private volatile boolean connected;
    private volatile String address, pairingCode;
    private volatile byte[] key;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!connected) return;
            executor.execute(() -> {
                try {
                    byte[] frame = requestFrame();
                    if (!connected) return;
                    if (frame != null && frame.length>0) {
                        Bitmap b=BitmapFactory.decodeByteArray(frame,0,frame.length);
                        runOnUiThread(() -> { if (connected && b != null) { preview.setImageBitmap(b); status.setText("Sesión activa · Wi-Fi local"); } });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> { if (connected) status.setText("Esperando pantalla: " + e.getMessage()); });
                }
                runOnUiThread(() -> { if (connected) main.postDelayed(poll, 850); });
            });
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(0xff161124);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(48), dp(22), dp(24));
        scroll.addView(root);
        root.addView(label("Root Supervisor Parent",25,0xffe6dbff));
        root.addView(label("Solo puedes ver una sesión iniciada y autorizada en el otro teléfono. Ambos dispositivos deben estar en la misma red Wi-Fi.",15,0xffffffff));
        ip = field("IP mostrada en Child (ej. 192.168.1.9)");
        code = field("Código temporal mostrado en Child");
        root.addView(ip); root.addView(code);
        Button connect = button("Conectar a la sesión"); connect.setOnClickListener(v -> connect()); root.addView(connect);
        Button disconnect = button("Dejar de visualizar"); disconnect.setOnClickListener(v -> disconnect()); root.addView(disconnect);
        status=label("Sin conexión",15,0xffe6dbff); root.addView(status);
        preview = new ImageView(this);
        preview.setBackgroundColor(0xff09070d);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(440));
        root.addView(preview,p);
        root.addView(label("El teléfono que comparte puede detener la sesión en cualquier momento. No hay acceso al micrófono, controles de pantalla, capturas guardadas ni transmisión por Internet.",13,0xffc4bdd2));
        setContentView(scroll);
    }

    private void connect() {
        disconnect();
        address=ip.getText().toString().trim();
        pairingCode=code.getText().toString().trim().toLowerCase(Locale.US);
        if (!address.matches("(?:\\d{1,3}\\.){3}\\d{1,3}") || !pairingCode.matches("[0-9a-f]{18}")) {
            status.setText("Introduce IP IPv4 y el código de 18 caracteres del otro teléfono."); return;
        }
        try { key=MessageDigest.getInstance("SHA-256").digest(pairingCode.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ex) { status.setText("Error de cifrado"); return; }
        connected=true;
        status.setText("Conectando…");
        main.post(poll);
    }

    private byte[] requestFrame() throws Exception {
        byte[] nonceBytes=new byte[12]; rng.nextBytes(nonceBytes);
        String nonce=hex(nonceBytes);
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key,"HmacSHA256"));
        String auth=hex(mac.doFinal(nonce.getBytes(StandardCharsets.UTF_8)));
        URL url = new URL("http://" + address + ":8765/frame");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        try {
            connection.setConnectTimeout(2500); connection.setReadTimeout(2500);
            connection.setRequestProperty("X-Nonce",nonce);
            connection.setRequestProperty("X-Auth",auth);
            int status=connection.getResponseCode();
            if (status==204) return null;
            if (status!=200) throw new IOException("HTTP " + status);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try (InputStream in=connection.getInputStream()) {
                byte[] chunk=new byte[16384]; int count;
                while ((count=in.read(chunk))!=-1) {
                    if (out.size()+count>4_000_000) throw new IOException("Imagen demasiado grande");
                    out.write(chunk,0,count);
                }
            }
            byte[] payload=out.toByteArray();
            if (payload.length<29) throw new IOException("Paquete incompleto");
            byte[] iv=Arrays.copyOfRange(payload,0,12);
            byte[] ciphertext=Arrays.copyOfRange(payload,12,payload.length);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            return cipher.doFinal(ciphertext);
        } finally { connection.disconnect(); }
    }

    private void disconnect() {
        connected=false;
        main.removeCallbacks(poll);
        if (preview!=null) preview.setImageDrawable(null);
        if (status!=null) status.setText("Sin conexión");
        key=null;
        pairingCode=null;
    }

    @Override protected void onDestroy() { disconnect(); executor.shutdownNow(); super.onDestroy(); }
    private int dp(int x) { return Math.round(x*getResources().getDisplayMetrics().density); }
    private TextView label(String s,int size,int color) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,dp(10),0,dp(10));t.setGravity(Gravity.START);return t;
    }
    private EditText field(String hint) {
        EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);
        e.setTextColor(0xffffffff); e.setHintTextColor(0xffaaa2be);
        e.setPadding(dp(12),dp(12),dp(12),dp(12));return e;
    }
    private Button button(String s) {
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(0xffffffff);
        b.setBackgroundTintList(ColorStateList.valueOf(0xff6443a7));return b;
    }
    private static String hex(byte[] data) {
        StringBuilder b=new StringBuilder();
        for(byte v:data)b.append(String.format(Locale.US,"%02x",v&255));
        return b.toString();
    }
}
