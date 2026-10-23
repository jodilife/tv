package com.widge.cctv.tuya;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

public class TuyaModuleCardService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.gathin.tvhome.action.MODULE_CARD".equals(intent.getAction())) {
            try {
                SharedPreferences prefs = getSharedPreferences("tuya_cams", MODE_PRIVATE);
                Map<String, ?> all = prefs.getAll();
                JSONArray cards = new JSONArray();

                for (String key : all.keySet()) {
                    if (!key.startsWith("cam_")) continue;
                    String data = String.valueOf(all.get(key));
                    String[] parts = data.split("\\|", 5);
                    if (parts.length >= 4) {
                        String ip = parts[0];
                        String brand = parts.length > 4 ? parts[4] : "Bardi";

                        JSONObject card = new JSONObject();
                        card.put("id", key);
                        card.put("label", brand + " CCTV");
                        card.put("caption", ip + " - Tuya / ONVIF");
                        card.put("action", "com.widge.cctv.tuya.MainActivity");
                        if ("Bardi".equals(brand)) {
                            card.put("glow", "#FF6B00");
                        } else if ("HanRiver".equals(brand)) {
                            card.put("glow", "#00A8FF");
                        } else {
                            card.put("glow", "#FFD700");
                        }
                        cards.put(card);
                    }
                }

                Intent result = new Intent("com.gathin.tvhome.action.MODULE_CARD_RESULT");
                result.putExtra("cards", cards.toString());
                result.putExtra("package", getPackageName());
                sendBroadcast(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return START_NOT_STICKY;
    }
}