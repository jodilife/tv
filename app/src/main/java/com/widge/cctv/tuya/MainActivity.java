package com.widge.cctv.tuya;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private LinearLayout cameraList;
    private VideoView videoView;
    private EditText ipInput, userInput, passInput, nameInput;
    private TextView statusText, helpText;
    private SharedPreferences prefs;
    private String currentRtsp = "";

    private static class CameraEntry {
        final String ip;
        final String user;
        final String pass;
        final String rtsp;
        final String brand;
        final String key;

        CameraEntry(String ip, String user, String pass, String rtsp, String brand, String key) {
            this.ip = ip;
            this.user = user;
            this.pass = pass;
            this.rtsp = rtsp;
            this.brand = brand;
            this.key = key;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraList = findViewById(R.id.camera_list);
        videoView = findViewById(R.id.video_view);
        ipInput = findViewById(R.id.ip_input);
        userInput = findViewById(R.id.user_input);
        passInput = findViewById(R.id.pass_input);
        nameInput = findViewById(R.id.name_input);
        statusText = findViewById(R.id.status);
        helpText = findViewById(R.id.help);
        prefs = getSharedPreferences("tuya_cams", MODE_PRIVATE);

        helpText.setText("Bardi & Han River CCTV launcher\nRTSP direct view • Tuya device list • Android TV ready");
        loadSavedCameras();

        Button bardi = findViewById(R.id.btn_add_bardi);
        Button hanRiver = findViewById(R.id.btn_add_hanriver);
        Button custom = findViewById(R.id.btn_add_custom);
        Button discover = findViewById(R.id.btn_discover);
        Button pip = findViewById(R.id.btn_pip);
        Button stop = findViewById(R.id.btn_stop);

        bardi.setOnClickListener(v -> addBardiCamera());
        hanRiver.setOnClickListener(v -> addHanRiverCamera());
        custom.setOnClickListener(v -> addCustomCamera());
        discover.setOnClickListener(v -> addCustomCamera());
        pip.setOnClickListener(v -> enterPip());
        stop.setOnClickListener(v -> stopVideo());
    }

    private void loadSavedCameras() {
        cameraList.removeAllViews();
        Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (!key.startsWith("cam_")) continue;
            String data = String.valueOf(all.get(key));
            String[] parts = data.split("\\|", 5);
            if (parts.length >= 4) {
                String ip = parts[0];
                String user = parts[1];
                String pass = parts[2];
                String rtsp = parts[3];
                String brand = parts.length > 4 ? parts[4] : "Bardi";
                addCameraTile(new CameraEntry(ip, user, pass, rtsp, brand, key));
            }
        }

        if (all.isEmpty()) {
            statusText.setText("Belum ada kamera tersimpan. Tambahkan Bardi, Han River, atau custom RTSP.");
        }
    }

    private void addCameraTile(CameraEntry entry) {
        View tile = getLayoutInflater().inflate(R.layout.item_camera, null);
        TextView nameView = tile.findViewById(R.id.cam_name);
        TextView ipView = tile.findViewById(R.id.cam_ip);
        TextView brandView = tile.findViewById(R.id.cam_brand);

        String displayName = "Bardi " + entry.ip;
        if ("HanRiver".equals(entry.brand)) {
            displayName = "Han River " + entry.ip;
        } else if ("Custom".equals(entry.brand)) {
            displayName = "Custom " + entry.ip;
        }

        final String finalName = displayName;
        final String finalRtsp = entry.rtsp;

        nameView.setText(finalName);
        ipView.setText(entry.rtsp.length() > 45 ? entry.rtsp.substring(0, 45) + "..." : entry.rtsp);
        brandView.setText(entry.brand + " | Tuya | Android TV");
        tile.setFocusable(true);

        tile.setOnClickListener(v -> playRtsp(finalRtsp, finalName));
        tile.setOnLongClickListener(v -> {
            prefs.edit().remove(entry.key).apply();
            loadSavedCameras();
            Toast.makeText(this, "Hapus " + entry.ip, Toast.LENGTH_SHORT).show();
            return true;
        });

        cameraList.addView(tile);
    }

    private void addBardiCamera() {
        String ip = ipInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();

        if (ip.isEmpty()) {
            Toast.makeText(this, "Isi IP Bardi", Toast.LENGTH_SHORT).show();
            return;
        }
        if (user.isEmpty()) user = "admin";
        if (pass.isEmpty()) pass = "123456";

        String rtsp = "rtsp://" + user + ":" + pass + "@" + ip + ":8554/profile0";
        String key = "cam_" + ip.replace(".", "_") + "_bardi_" + System.currentTimeMillis();
        prefs.edit().putString(key, ip + "|" + user + "|" + pass + "|" + rtsp + "|Bardi").apply();
        addCameraTile(new CameraEntry(ip, user, pass, rtsp, "Bardi", key));
        playRtsp(rtsp, "Bardi " + ip);
    }

    private void addHanRiverCamera() {
        String ip = ipInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();

        if (ip.isEmpty()) {
            Toast.makeText(this, "Isi IP Han River", Toast.LENGTH_SHORT).show();
            return;
        }
        if (user.isEmpty()) user = "admin";

        String rtsp = "rtsp://" + user + ":" + pass + "@" + ip + ":8554/profile0";
        String key = "cam_" + ip.replace(".", "_") + "_hanriver_" + System.currentTimeMillis();
        prefs.edit().putString(key, ip + "|" + user + "|" + pass + "|" + rtsp + "|HanRiver").apply();
        addCameraTile(new CameraEntry(ip, user, pass, rtsp, "HanRiver", key));
        playRtsp(rtsp, "Han River " + ip);
    }

    private void addCustomCamera() {
        String ip = ipInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();
        String customUrl = nameInput.getText().toString().trim();

        if (ip.isEmpty() && customUrl.isEmpty()) {
            Toast.makeText(this, "Isi IP atau RTSP URL", Toast.LENGTH_SHORT).show();
            return;
        }

        String rtsp = customUrl.isEmpty() ? "rtsp://" + user + ":" + pass + "@" + ip + ":554/stream1" : customUrl;
        String key = "cam_" + (ip.isEmpty() ? "custom" : ip.replace(".", "_")) + "_custom_" + System.currentTimeMillis();
        prefs.edit().putString(key, (ip.isEmpty() ? "0.0.0.0" : ip) + "|" + user + "|" + pass + "|" + rtsp + "|Custom").apply();
        addCameraTile(new CameraEntry(ip.isEmpty() ? "Custom" : ip, user, pass, rtsp, "Custom", key));
        playRtsp(rtsp, "Custom " + (ip.isEmpty() ? "RTSP" : ip));
    }

    private void discoverTuyaCameras() {
        statusText.setText("Scanning 192.168.1.x pada port 8554 & 554...");

        new AsyncTask<Void, Void, List<String>>() {
            @Override
            protected List<String> doInBackground(Void... voids) {
                List<String> found = new ArrayList<>();
                for (int i = 1; i < 255; i++) {
                    String ip = "192.168.1." + i;

                    try {
                        java.net.Socket s = new java.net.Socket();
                        s.connect(new java.net.InetSocketAddress(ip, 8554), 180);
                        s.close();
                        found.add(ip + ":8554");
                    } catch (Exception ignored) {
                    }

                    try {
                        java.net.Socket s = new java.net.Socket();
                        s.connect(new java.net.InetSocketAddress(ip, 554), 180);
                        s.close();
                        if (!found.contains(ip + ":554")) {
                            found.add(ip + ":554");
                        }
                    } catch (Exception ignored) {
                    }

                    if (found.size() >= 8) break;
                }
                return found;
            }

            @Override
            protected void onPostExecute(List<String> ips) {
                statusText.setText("Scan selesai: " + ips);
                for (String ipPort : ips) {
                    String ip = ipPort.split(":")[0];
                    String key = "cam_" + ip.replace(".", "_") + "_discovered";
                    if (!prefs.contains(key)) {
                        String rtsp = "rtsp://admin:123456@" + ip + ":8554/profile0";
                        prefs.edit().putString(key, ip + "|admin|123456|" + rtsp + "|Bardi").apply();
                        addCameraTile(new CameraEntry(ip, "admin", "123456", rtsp, "Bardi", key));
                    }
                }
            }
        }.execute();
    }

    private void playRtsp(String rtspUrl, String title) {
        try {
            currentRtsp = rtspUrl;
            videoView.setVideoPath(rtspUrl);
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                videoView.start();
                statusText.setText("Playing " + title + " - " + rtspUrl);
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                statusText.setText("RTSP gagal diputar di device ini. Coba URL lain atau gunakan player eksternal. Error: " + what + "," + extra + "\n" + rtspUrl);
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    intent.setDataAndType(android.net.Uri.parse(rtspUrl), "video/*");
                    startActivity(intent);
                } catch (Exception ignored) {
                }
                return true;
            });
            statusText.setText("Connecting " + title + " ...");
            videoView.start();
        } catch (Exception e) {
            statusText.setText("Error: " + e.getMessage());
        }
    }

    private void stopVideo() {
        try {
            videoView.stopPlayback();
            statusText.setText("Stopped");
        } catch (Exception ignored) {
        }
    }

    private void enterPip() {
        if (currentRtsp.isEmpty()) {
            Toast.makeText(this, "Mainkan CCTV dulu", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "PIP tidak didukung pada Android versi ini", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Rational ratio = new Rational(16, 9);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .build();
            enterPictureInPictureMode(params);
        } catch (Exception e) {
            Toast.makeText(this, "PIP gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (videoView.isPlaying()) {
                stopVideo();
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            discoverTuyaCameras();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            videoView.stopPlayback();
        } catch (Exception ignored) {
        }
    }
}