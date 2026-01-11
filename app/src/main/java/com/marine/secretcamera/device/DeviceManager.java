package com.marine.secretcamera.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class DeviceManager {
    private static final String PREF_NAME = "device_info_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static volatile DeviceManager instance;
    private final DeviceInfo deviceInfo;

    public static DeviceManager getInstace(Context context) {
      if(instance == null) {
        synchronized (DeviceManager.class){
          if (instance == null) {
            instance = new DeviceManager(context.getApplicationContext());
          }
        }
      }
      return instance;
    }

    public DeviceInfo getDeviceInfo() {
      return this.deviceInfo;
    }
    private DeviceManager(Context context) {
      SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

      String deviceId = prefs.getString(KEY_DEVICE_ID, null);
      String deviceName = prefs.getString(KEY_DEVICE_NAME, null);
      if(deviceId == null) {
        deviceId = generateDeviceId();
        deviceName =generateDeviceName();

        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_NAME, deviceName)
            .apply();
      }
      this.deviceInfo = new DeviceInfo(deviceId, deviceName);
    }
    private String generateDeviceName() {
      String manufacturer = Build.MANUFACTURER;
      String model = Build.MODEL;
      if(model.toLowerCase().startsWith(manufacturer.toLowerCase())){
        return model;
      }else {
        return manufacturer + " " + model;
      }
    }

  private String generateDeviceId() {
    String fingerprint =
        Build.BRAND + "|" +
            Build.MANUFACTURER + "|" +
            Build.MODEL + "|" +
            Build.DEVICE + "|" +
            Build.BOARD;

    return sha256(fingerprint);
  }

  private String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 8; i++) { // 取前 8 byte 即可（16 hex）
        sb.append(String.format("%02x", bytes[i]));
      }
      return sb.toString();
    } catch (Exception e) {
      return UUID.randomUUID().toString();
    }
  }

}
