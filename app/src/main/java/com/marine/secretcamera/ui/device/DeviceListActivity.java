package com.marine.secretcamera.ui.device;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.marine.secretcamera.R;
import com.marine.secretcamera.device.DeviceInfo;
import com.marine.secretcamera.device.DeviceManager;
import com.marine.secretcamera.net.OkHttpManager;
import com.marine.secretcamera.pojo.WebSocketEnvelop;
import com.marine.secretcamera.ui.camera.CameraActivity;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class DeviceListActivity extends AppCompatActivity {
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private static final String WS_URL = "ws://47.108.73.56:8080/ws";
  private WebSocket websocket;
  private final ArrayList<DeviceInfo> deviceInfos = new ArrayList<>();
  private DeviceAdapter deviceAdapter;
  private SwipeRefreshLayout swipeRefreshLayout;
  private final Gson gson = new Gson();

  // private OkHttpClient client;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_device_list);

    swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
    // client = new OkHttpClient.Builder()
    // .connectTimeout(5, TimeUnit.SECONDS)
    // .readTimeout(5, TimeUnit.SECONDS)
    // .writeTimeout(5, TimeUnit.SECONDS)
    // .build();

    RecyclerView recyclerView = findViewById(R.id.rvDeviceList);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));

    // 初始化 adapter，先使用空数据
    deviceAdapter = new DeviceAdapter(deviceInfos);
    recyclerView.setAdapter(deviceAdapter);

    swipeRefreshLayout.setOnRefreshListener(this::fetchDeviceInfos);

    // 上线
    connectWebSocket();

    Button btnStartPush = findViewById(R.id.btnStartPush);
    btnStartPush.setOnClickListener(v -> {
      Intent intent = new Intent(DeviceListActivity.this, CameraActivity.class);
      startActivity(intent);
    });

    swipeRefreshLayout.setRefreshing(true);
    // 拉取设备列表
    fetchDeviceInfos();
  }

  private void connectWebSocket() {
    DeviceInfo me  = DeviceManager.getInstace(this).getDeviceInfo();
    Request request = new Request.Builder().url(WS_URL).build();
    websocket = OkHttpManager.getInstance().newWebSocket(request, new WebSocketListener() {
      @Override
      public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "goOnline");
        map.put("data", me);
        websocket.send(gson.toJson(map));
      }

      @Override
      public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
        Log.d("DeviceListActivity", " onMessage: " + text);
        WebSocketEnvelop envelop = gson.fromJson(text, WebSocketEnvelop.class);
        if (!"push_request".equals(envelop.getType())) {
          return;
        }
        if (envelop.getData() == null) return;
        DeviceInfo me = DeviceManager.getInstace(DeviceListActivity.this).getDeviceInfo();
        if (!me.deviceId.equals(envelop.getData().getTargetDeviceId())) {
          return;
        }
        Long expireAt = envelop.getData().getExpireAt();
        if (System.currentTimeMillis() > expireAt) {
          Log.w("DeviceListActivity", "received expired message: " + text);
          return;
        }
        // todo: 这里可以弹一个对话框，询问用户是否接受推流请求
        runOnUiThread(() -> {
          Toast.makeText(
              DeviceListActivity.this,
              "收到来自设备 " + envelop.getData().getFromDeviceId() + " 的推流请求",
              Toast.LENGTH_LONG
          ).show();
        });
      }

      @Override
      public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        super.onClosed(webSocket, code, reason);
      }

      @Override
      public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        super.onClosing(webSocket, code, reason);
      }

      @Override
      public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
        super.onFailure(webSocket, t, response);
      }

      @Override
      public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
        super.onMessage(webSocket, bytes);
      }
    });
  }

  private void fetchDeviceInfos() {
    Request request = new Request.Builder()
        .url("http://47.108.73.56:8080/api/online_devices")
        .build();
    OkHttpManager.getInstance().newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e) {
        Log.e("DeviceListActivity", "Failed to fetch device info", e);
        mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "获取设备列表失败", Toast.LENGTH_SHORT).show());
        swipeRefreshLayout.setRefreshing(false);
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response) {
        try {
          if (!response.isSuccessful()) {
            Log.e("DeviceListActivity", "server returned wrong message");
            mainHandler.post(() -> Toast
                .makeText(DeviceListActivity.this, "server returned wrong message", Toast.LENGTH_SHORT).show());
            return;
          }

          ResponseBody body = response.body();
          if (body == null) {
            Log.e("DeviceListActivity", "response body is null");
            mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "服务器响应为空", Toast.LENGTH_SHORT).show());
            return;
          }

          String json;
          try {
            json = body.string();
          } catch (Exception e) {
            Log.e("DeviceListActivity", "Failed to read response body", e);
            mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "无法解析服务器响应", Toast.LENGTH_SHORT).show());
            return;
          } finally {
            body.close();
          }

          Log.d("DeviceListActivity", "onResponse: " + json);

          try {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<DeviceInfo>>() {
            }.getType();
            final List<DeviceInfo> fetched = gson.fromJson(json, listType);
            mainHandler.post(() -> {
              deviceInfos.clear();
              deviceInfos.addAll(fetched);
              deviceAdapter.notifyDataSetChanged();
            });
          } catch (Exception ex) {
            Log.e("DeviceListActivity", "Failed to parse device info", ex);
            mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "解析设备列表失败", Toast.LENGTH_SHORT).show());
          }
        } catch (Exception e) {
          Log.e("DeviceListActivity", "Failed to read response body", e);
          mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "解析设备列表失败", Toast.LENGTH_SHORT).show());
        } finally {
          mainHandler.post(() -> swipeRefreshLayout.setRefreshing(false));
        }
      }
    });
  }
}
