package com.marine.secretcamera.ui.device;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.marine.secretcamera.R;
import com.marine.secretcamera.device.DeviceInfo;
import com.marine.secretcamera.device.DeviceManager;
import com.marine.secretcamera.net.WebSocketManager;
import com.marine.secretcamera.ui.camera.CameraActivity;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DeviceListActivity extends AppCompatActivity {
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final ArrayList<DeviceInfo> deviceInfos = new ArrayList<>();
  private DeviceAdapter deviceAdapter;
  private SwipeRefreshLayout swipeRefreshLayout;

  private final OkHttpClient client = new OkHttpClient();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_device_list);

    swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);


    RecyclerView recyclerView = findViewById(R.id.rvDeviceList);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));

    // 初始化 adapter，先使用空数据
    deviceAdapter = new DeviceAdapter(deviceInfos);
    recyclerView.setAdapter(deviceAdapter);

    swipeRefreshLayout.setOnRefreshListener(this::fetchDeviceInfos);

    // 上线
    DeviceInfo deviceInfo = DeviceManager.getInstace(this).getDeviceInfo();
    WebSocketManager webSocketManager = WebSocketManager.getInstance();
    webSocketManager.setOnOpenListener(() -> {
      Log.i("DeviceListActivity", "webSocket now opened, going online");
      webSocketManager.goOnline(deviceInfo);
    });

    webSocketManager.connect();

    Button btnStartPush = findViewById(R.id.btnStartPush);
    btnStartPush.setOnClickListener(v -> {
      Intent intent = new Intent(DeviceListActivity.this, CameraActivity.class);
      startActivity(intent);
    });

    swipeRefreshLayout.setRefreshing(true);
    // 拉取设备列表
    fetchDeviceInfos();
  }

  private void fetchDeviceInfos() {
    Request request = new Request.Builder()
        .url("http://47.108.73.56:8080/api/online_devices")
        .build();
    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e) {
        Log.e("DeviceListActivity", "Failed to fetch device info", e);
        mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "获取设备列表失败", Toast.LENGTH_SHORT).show());
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response) {
        try {
          if (!response.isSuccessful()) {
            Log.e("DeviceListActivity", "server returned wrong message");
            mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "server returned wrong message", Toast.LENGTH_SHORT).show());
            return;
          }

          ResponseBody body = response.body();
          if (body == null) {
            Log.e("DeviceListActivity", "response body is null");
            mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "无法读取服务器响应", Toast.LENGTH_SHORT).show());
            return;
          }

          String json;
          try {
            json = body.string();
          } catch (Exception e) {
            Log.e("DeviceListActivity", "Failed to read response body", e);
            mainHandler.post(() -> Toast.makeText(DeviceListActivity.this, "无法读取服务器响应", Toast.LENGTH_SHORT).show());
            return;
          } finally {
            body.close();
          }

          Log.d("DeviceListActivity", "onResponse: " + json);

          try {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<DeviceInfo>>(){}.getType();
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
