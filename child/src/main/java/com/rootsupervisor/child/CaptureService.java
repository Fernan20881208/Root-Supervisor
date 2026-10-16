package com.rootsupervisor.child;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class CaptureService extends Service {
    private static final String CHANNEL = "visible_capture";
    private static volatile boolean active = false;
    private static volatile String status = "Sin transmisión activa.";
    private volatile byte[] latestFrame;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread worker;
    private Handler workHandler;
    private LocalServer server;
    private volatile boolean closed = false;
    private long lastFrameMs = 0;

    public static boolean isActive() { return active; }
    public static String getStatus() { return status; }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf(); return START_NOT_STICKY;
        }
        if (active || intent == null || !intent.hasExtra("projectionData")) return START_NOT_STICKY;
        closed = false;
        createNotification();
        try {
            worker = new HandlerThread("capture_worker");
            worker.start();
            workHandler = new Handler(worker.getLooper());

            byte[] secret = new byte[9];
            new SecureRandom().nextBytes(secret);
            String code = hex(secret);
            byte[] key = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            server = new LocalServer(key);
            server.start();

            MediaProjectionManager mgr = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent authorization = intent.getParcelableExtra("projectionData");
            projection = mgr.getMediaProjection(intent.getIntExtra("resultCode", Activity.RESULT_CANCELED), authorization);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, workHandler);

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = 640;
            int height = Math.max(320, Math.round(640f * metrics.heightPixels / metrics.widthPixels));
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(this::receiveImage, workHandler);
            display = projection.createVirtualDisplay("Visible Family Share", width, height,
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, workHandler);
            active = true;
            status = "TRANSMITIENDO (visible)\nIP: " + server.localIp() +
                     "\nPuerto: 8765\nCódigo temporal: " + code +
                     "\n\nAbre Root Supervisor Parent para visualizar. Pulsa Detener para finalizar.";
            return START_NOT_STICKY;
        } catch (Exception e) {
            status = "No se pudo iniciar: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void createNotification() {
        NotificationManager mgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mgr.createNotificationChannel(new NotificationChannel(CHANNEL, "Pantalla compartida", NotificationManager.IMPORTANCE_HIGH));
        Intent home = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 1, home, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, CaptureService.class).setAction("STOP");
        PendingIntent stop = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Root Supervisor: pantalla compartida")
                .setContentText("Sesión autorizada. Toca para abrir.")
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_delete, "Detener", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(102, notification);
    }

    private void receiveImage(ImageReader r) {
        Image image = null;
        try {
            image = r.acquireLatestImage();
            if (image == null) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastFrameMs < 650) return;
            lastFrameMs = now;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer pixels = plane.getBuffer();
            int stride = plane.getPixelStride();
            int totalWidth = plane.getRowStride() / stride;
            Bitmap padded = Bitmap.createBitmap(totalWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(pixels);
            Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.JPEG, 65, out);
            latestFrame = out.toByteArray();
            cropped.recycle();
            padded.recycle();
        } catch (Exception ignored) {
        } finally { if (image != null) image.close(); }
    }

    @Override public void onDestroy() {
        if (!closed) {
            closed = true; active = false;
            if (server != null) server.close();
            if (display != null) { display.release(); display = null; }
            if (reader != null) { reader.close(); reader = null; }
            if (projection != null) {
                MediaProjection p = projection; projection = null; p.stop();
            }
            if (worker != null) worker.quitSafely();
        }
        status = "Sin transmisión activa.";
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private static String hex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for (byte b : a) sb.append(String.format(Locale.US,"%02x", b & 255));
        return sb.toString();
    }

    private final class LocalServer {
        private final byte[] key;
        private final ServerSocket socket;
        private final ExecutorService pool = Executors.newFixedThreadPool(4);
        private final SecureRandom rng = new SecureRandom();
        private final HashSet<String> seen = new HashSet<>();
        private Thread acceptThread;

        LocalServer(byte[] key) throws Exception {
            this.key = key;
            InetAddress wlan = wifiIp();
            if (wlan == null) throw new IOException("Se necesita Wi-Fi con dirección IPv4");
            socket = new ServerSocket(8765, 8, wlan);
        }

        String localIp() { return socket.getInetAddress().getHostAddress(); }
        void start() {
            acceptThread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try { Socket client = socket.accept(); pool.execute(() -> respond(client)); }
                    catch (Exception ex) { if (socket.isClosed()) return; }
                }
            }, "local_visible_stream");
            acceptThread.start();
        }
        void close() {
            try { socket.close(); } catch (IOException ignored) {}
            pool.shutdownNow();
        }
        void respond(Socket client) {
            try (Socket s = client) {
                s.setSoTimeout(5000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                OutputStream out = s.getOutputStream();
                String request = in.readLine();
                if (request == null || !request.startsWith("GET /frame ")) { write(out,404,new byte[0]); return; }
                String nonce = null, mac = null, line;
                int headers = 0;
                while ((line=in.readLine()) != null && !line.isEmpty() && headers++ < 35) {
                    if (line.regionMatches(true,0,"X-Nonce:",0,8)) nonce=line.substring(8).trim();
                    if (line.regionMatches(true,0,"X-Auth:",0,7)) mac=line.substring(7).trim();
                }
                if (!authorized(nonce, mac)) { write(out, 403, new byte[0]); return; }
                byte[] frame = latestFrame;
                if (frame == null) { write(out, 204, new byte[0]); return; }
                byte[] iv = new byte[12]; rng.nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
                byte[] ciphertext = cipher.doFinal(frame);
                byte[] payload = new byte[iv.length + ciphertext.length];
                System.arraycopy(iv,0,payload,0,iv.length);
                System.arraycopy(ciphertext,0,payload,iv.length,ciphertext.length);
                write(out,200,payload);
            } catch (Exception ignored) {}
        }
        private synchronized boolean authorized(String nonce, String auth) {
            if (nonce == null || !nonce.matches("[0-9a-f]{24}") || auth == null || !auth.matches("[0-9a-f]{64}") || seen.contains(nonce)) return false;
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key, "HmacSHA256"));
                String expected = hex(mac.doFinal(nonce.getBytes(StandardCharsets.UTF_8)));
                if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), auth.getBytes(StandardCharsets.US_ASCII))) return false;
                if (seen.size() > 1000) seen.clear();
                seen.add(nonce);
                return true;
            } catch (Exception e) { return false; }
        }
        private void write(OutputStream out, int status, byte[] body) throws IOException {
            String text = status==200 ? "OK" : status==204 ? "No Content" : status==403 ? "Forbidden" : "Not Found";
            String head = "HTTP/1.1 "+status+" "+text+"\r\nContent-Type: application/octet-stream\r\nCache-Control: no-store\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body); out.flush();
        }
        private InetAddress wifiIp() throws SocketException {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all.hasMoreElements()) {
                NetworkInterface iface = all.nextElement();
                if (!iface.isUp() || !iface.getName().startsWith("wlan")) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress ip = addrs.nextElement();
                    if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) return ip;
                }
            }
            return null;
        }
    }
}
