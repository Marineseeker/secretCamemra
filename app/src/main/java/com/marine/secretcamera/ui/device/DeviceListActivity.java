package com.marine.secretcamera.ui.device;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.marine.secretcamera.R;
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

    Button btnStartPush = findViewById(R.id.btnStartPush);
    btnStartPush.setOnClickListener(v -> {
      Intent intent = new Intent(DeviceListActivity.this, CameraActivity.class);
      startActivity(intent);
    });
  }
}
