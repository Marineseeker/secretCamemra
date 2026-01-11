package com.marine.secretcamera.device;

public class DeviceInfo {
    public String deviceId;
    public String deviceName;
    public boolean isOnline;
    public DeviceInfo(String deviceId, String deviceName) {
      this.deviceId = deviceId;
      this.deviceName = deviceName;
      this.isOnline = false;
    }
}
