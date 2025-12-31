package com.marine.secretcamera.ui.device;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.marine.secretcamera.R;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

  private final List<String> devices;

  public DeviceAdapter(List<String> devices) {
    this.devices = devices;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_device, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.tvDeviceName.setText(devices.get(position));
    holder.tvDeviceStatus.setText("在线");
  }

  @Override
  public int getItemCount() {
    return devices.size();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    TextView tvDeviceName;
    TextView tvDeviceStatus;

    ViewHolder(View itemView) {
      super(itemView);
      tvDeviceName = itemView.findViewById(R.id.tvDeviceName);
      tvDeviceStatus = itemView.findViewById(R.id.tvDeviceStatus);
    }
  }
}
