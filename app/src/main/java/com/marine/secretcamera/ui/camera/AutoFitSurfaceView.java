// D:/Android_Application/secretCamera/app/src/main/java/com/marine/secretcamera/ui/camera/AutoFitSurfaceView.java

package com.marine.secretcamera.ui.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

public class AutoFitSurfaceView extends SurfaceView {

  private int aspectRatioWidth = 0;
  private int aspectRatioHeight = 0;

  public AutoFitSurfaceView(Context context) {
    super(context);
  }

  public AutoFitSurfaceView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AutoFitSurfaceView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  /**
   * 设置期望的宽高比。
   *
   * @param width  比例的宽度部分
   * @param height 比例的高度部分
   */
  public void setAspectRatio(int width, int height) {
    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("Size cannot be negative.");
    }
    aspectRatioWidth = width;
    aspectRatioHeight = height;
    // 重新请求布局以应用新的宽高比
    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);

    if (aspectRatioWidth == 0 || aspectRatioHeight == 0) {
      // 如果没有设置宽高比，则使用默认测量值
      setMeasuredDimension(width, height);
    } else {
      // 根据给定的宽度，计算符合宽高比的高度
      int newHeight = width * aspectRatioHeight / aspectRatioWidth;
      setMeasuredDimension(width, newHeight);
    }
  }
}
