package com.marine.secretcamera.device;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class DeviceRepository {

    private static DeviceRepository instance = new DeviceRepository();
    private List<DeviceInfo> devices = new ArrayList<>();
    private final List<Runnable> observers = new ArrayList<>();

    public static DeviceRepository get() {
        return instance;
    }

    public void update(String json) {
        devices = new Gson().fromJson(
                json,
                new TypeToken<List<DeviceInfo>>(){}.getType()
        );
        notifyObservers();
    }

    public List<DeviceInfo> getDevices() {
        return devices;
    }

    public void observe(Runnable observer) {
        observers.add(observer);
    }

    private void notifyObservers() {
        for (Runnable r : observers) r.run();
    }
}
