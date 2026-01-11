package com.marine.secretcamera.ui.device;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.marine.secretcamera.R;
import com.marine.secretcamera.device.DeviceInfo;
import com.marine.secretcamera.device.DeviceManager;
import com.marine.secretcamera.net.WebSocketManager;
import com.marine.secretcamera.ui.camera.CameraActivity;

import java.util.ArrayList;
import java.util.List;

public class DeviceListActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_device_list);

    RecyclerView recyclerView = findViewById(R.id.rvDeviceList);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));

    // 示例数据
    List<String> devices = new ArrayList<>();
    devices.add("Camera-001（在线）");
    devices.add("Camera-002（在线）");
    devices.add("Camera-003（在线）");
    recyclerView.setAdapter(new DeviceAdapter(devices));

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
  }
}
