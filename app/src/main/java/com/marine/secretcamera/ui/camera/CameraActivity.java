package com.marine.secretcamera.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.marine.secretcamera.R;

import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {
  private SurfaceView surfaceView;
  private CameraDevice cameraDevice;
  private CameraCaptureSession cameraCaptureSession;
  private String cameraId;
  private CameraManager cameraManager;
  private HandlerThread cameraThread;
  private Handler cameraHandler;
  private Surface previewSurface;

  private final ActivityResultLauncher<String> cameraPermissionLauncher =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean isGranted) {
              if (!isGranted) {
                finish();
              }
            }
          }
      );

  private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
      startCameraThread();
      setCameraId();
      // 永远只应该在surfaceCreated()中进入startCamera();
      // 永远应该在进入startCamera()之前检查权限
      if (ContextCompat.checkSelfPermission(CameraActivity.this,
          Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        startCamera(cameraId);
      }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
      closeCamera();
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);

    setContentView(R.layout.activity_camera);

    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
      Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
      return insets;
    });

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    surfaceView = findViewById(R.id.surfaceView);
    SurfaceHolder holder = surfaceView.getHolder();
    holder.addCallback(surfaceCallback);
  }


  private void setCameraId() {
    try {
      cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
      for (String id : cameraManager.getCameraIdList()) {
        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
        Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
          cameraId = id;
          break;
        }
      }
    } catch (Exception e) {
      Log.e("CameraActivity", "Error getting camera ID", e);
    }
  }

  private void startCamera(String cameraId) {
    if (cameraManager == null) {
      return;
    }
    if (cameraHandler == null) {
      return;
    }
    if (cameraDevice != null) {
      return;
    }
    //  检查并确保已经有 CAMERA 权限；没有则发起请求并返回。
    if (ContextCompat.checkSelfPermission(CameraActivity.this,
        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      return;
    }

    //  调用 cameraManager.openCamera(...) 并传入 cameraHandler。
    try {
      cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler);
    } catch (CameraAccessException e) {
      Log.e("CameraActivity", "Error opening camera", e);
    }
  }


  private final CameraDevice.StateCallback cameraStateCallback =
      new CameraDevice.StateCallback() {
        //  在 CameraDevice.StateCallback.onOpened 中保存 cameraDevice，
        //  创建 CaptureRequest.Builder（TEMPLATE_PREVIEW），将预览 Surface 加入目标。
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
          cameraDevice = camera;
          createCameraSession();
        }

        //  在 onDisconnected / onError 中关闭并释放 cameraDevice。
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
          cameraDevice.close();
          cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
          Log.e("CameraActivity", "Error when opening camera");
          camera.close();
          cameraDevice = null;
        }
      };

  private void createCameraSession() {
    //  获取用于预览的 Surface（从 SurfaceView 的 SurfaceHolder）。
    previewSurface = surfaceView.getHolder().getSurface();
    if (previewSurface == null) {
      return;
    }
    List<Surface> outputs = new ArrayList<>();
    outputs.add(previewSurface);

    try {
      //  createCaptureSession(List<Surface> outputs,
      //  CameraCaptureSession.StateCallback callback,
      //  Handler handler)
      cameraDevice.createCaptureSession(
          outputs,
          sessionStateCallback,
          cameraHandler
      );
    } catch (CameraAccessException e) {
      Log.e("CameraActivity", "failed to create session");
    }
  }

  private final CameraCaptureSession.StateCallback sessionStateCallback =
      new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }

        //  调用 cameraDevice.createCaptureSession(...)，
        //  在 onConfigured 中保存 cameraCaptureSession，
        //  配置自动对焦等参数并调用 setRepeatingRequest(...) 开始预览流。
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
          //  CameraCaptureSession 表示一个活跃的相机捕获会话，
          //  负责管理从相机设备到多个输出 Surface（如 TextureView、ImageReader 等）的数据流，
          //  并执行 CaptureRequest。
          cameraCaptureSession = session;
          try {
            CaptureRequest.Builder builder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);

            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

            cameraCaptureSession.setRepeatingRequest(builder.build(), null, cameraHandler);
          } catch (CameraAccessException e) {
            Log.e("CameraActivity", "failed to start preview");
          }
        }
      };


  private void startCameraThread() {
    cameraThread = new HandlerThread("CameraThread");
    cameraThread.start();
    cameraHandler = new Handler(cameraThread.getLooper());
  }

  //  closeCamera() 负责停止重复请求、关闭 cameraCaptureSession、关闭 cameraDevice，并停止后台线程。
  private void closeCamera() {
    // todo 关闭摄像头, 关闭后台线程, 停止推流
    if (cameraCaptureSession != null) {
      cameraCaptureSession.close();
      cameraCaptureSession = null;
    }

    if (cameraDevice != null) {
      cameraDevice.close();
      cameraDevice = null;
    }

    if (cameraThread != null) {
      cameraThread.quitSafely();
      try {
        cameraThread.join();
        cameraThread = null;
        cameraHandler = null;
      } catch (InterruptedException e) {
        Log.e("CameraActivity", "Interrupted while quitting camera thread", e);
      }
    }
  }
}