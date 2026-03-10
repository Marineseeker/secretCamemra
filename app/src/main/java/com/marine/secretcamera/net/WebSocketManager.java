package com.marine.secretcamera.net;

import android.util.Log;

import com.google.gson.Gson;
import com.marine.secretcamera.device.DeviceInfo;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketManager extends WebSocketListener {

  private static final String TAG = "WebSocketManager";
  private static final String WEBSOCKET_URL = "ws://47.108.73.56:8080/ws";
  private static final WebSocketManager instance = new WebSocketManager();

  private  DeviceInfo deviceInfo;
  private WebSocket webSocket;
  private WebSocketManager() {}

  public static WebSocketManager getInstance() {
    return instance;
  }


  public void connect(DeviceInfo deviceInfo) {
    if (webSocket != null) {
      Log.d(TAG, "WebSocket is already connected or connecting.");
      return;
    }

    this.deviceInfo = deviceInfo;
    Request request = new Request.Builder().url(WEBSOCKET_URL).build();
    // 使用 OkHttpManager 的单例来创建 WebSocket，不再需要本地的 client
    webSocket = OkHttpManager.getInstance().newWebSocket(request, this);
  }

  public void goOnline() {
    if (webSocket == null || deviceInfo == null) {
      Log.e(TAG, "WebSocket not ready.");
      return;
    }

    Map<String, Object> map = new HashMap<>();
    map.put("type", "online");
    map.put("data", deviceInfo);
    String json = new Gson().toJson(map);
    Log.d(TAG, "goOnline: " + json);
    webSocket.send(json);
  }

  public void disconnect() {
    if (webSocket != null) {
      webSocket.close(1000, "User disconnected");
      webSocket = null;
    }
  }

  @Override
  public void onOpen(WebSocket webSocket, Response response) {
    Log.i(TAG, "WebSocket opened");
    goOnline();
  }

  @Override
  public void onMessage(WebSocket webSocket, String text) {
    Log.i(TAG, "Receiving: " + text);
  }

  @Override
  public void onClosing(WebSocket webSocket, int code, String reason) {
    Log.i(TAG, "Closing: " + code + " / " + reason);
    webSocket.close(1000, null);
  }

  @Override
  public void onClosed(WebSocket webSocket, int code, String reason) {
    Log.i(TAG, "Closed: " + code + " / " + reason);
    this.webSocket = null; // 清理引用
  }

  @Override
  public void onFailure(WebSocket webSocket, Throwable t, Response response) {
    Log.e(TAG, "Failure", t);
    this.webSocket = null; // 清理引用
  }
}

