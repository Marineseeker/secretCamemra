package com.marine.secretcamera.net;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.marine.secretcamera.device.DeviceInfo;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketManager {
  private static final WebSocketManager INSTANCE = new WebSocketManager();
  private OkHttpClient client;
  private WebSocket webSocket;
  private Request request;
  private volatile boolean connected = false;

  public interface OnOpenListener {
    void onOpen();
  }

  private OnOpenListener onOpenListener;

  public void setOnOpenListener(OnOpenListener listener) {
    this.onOpenListener = listener;
  }


  private final WebSocketListener listener = new WebSocketListener() {
    @Override
    public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
      Log.d("WebSocketManager", "WebSocket connection established");
      webSocket = ws;
      connected = true;
      if(onOpenListener != null) {
        onOpenListener.onOpen();
      }
    }
    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t,
                          @Nullable Response response) {
      Log.e("WebSocketManager", "failed to establish WebSocket connection");
      WebSocketManager.this.webSocket = null;
    }
    @Override
    public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
      WebSocketManager.this.webSocket = null;
    }
  };

  private WebSocketManager() {
    client = new OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build();

    request = new Request.Builder()
        .url("ws://47.108.73.56:8080/ws")
        .build();
  }

  public static WebSocketManager getInstance() {
    return INSTANCE;
  }

  public void connect() {
    Log.i("TAG", "connect: ");
    if (connected || webSocket != null) return;
    webSocket = client.newWebSocket(request, listener);
  }

  private boolean send(String msg) {
    if(!connected || webSocket == null) {
      return false;
    }
    return webSocket.send(msg);
  }
  public void goOnline(DeviceInfo device) {
    String msg = "{"
        + "\"type\":\"ONLINE\","
        + "\"deviceId\":\"" + device.deviceId + "\","
        + "\"deviceName\":\"" + device.deviceName + "\""
        + "}";
    device.isOnline = send(msg);
  }

  public void goOffline(DeviceInfo device) {
    String msg = "{"
        + "\"type\":\"OFFLINE\","
        + "\"deviceId\":\"" + device.deviceId + "\","
        + "\"deviceName\":\"" + device.deviceName + "\""
        + "}";
    device.isOnline = send(msg);
  }
}
