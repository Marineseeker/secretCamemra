package com.marine.secretcamera.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.marine.secretcamera.rtp.H264RtpPacketizer;
import com.marine.secretcamera.rtp.RtpSession;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoder {
  private static final String TAG = "VideoEncoder";
  private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

  private MediaCodec mediaCodec;
  private Surface inputSurface;
  private Thread drainThread;
  private volatile boolean running;

  private RtpSession rtpSession;

  public void setRtpSession(RtpSession rtpSession) {
    this.rtpSession = rtpSession;
  }

  private final H264RtpPacketizer packetizer = new H264RtpPacketizer();
  public Surface getInputSurface() {
    return inputSurface;
  }

  // return the surface to be used in CameraActivity
  public Surface prepare(int width, int height, int fps, int bitrate) throws IOException {
    // MediaFormat 定义了输入和输出视频流的各种属性，最终决定了输出的 H.264 视频流将遵循什么样的规范。
    MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
    // 设置比特率 它规定了编码器每秒钟可以用多少数据来描述视频画面。
    // 例如，如果您设置的 bitrate 是 2,000,000 (即 2 Mbps)，编码器就会尽量将每秒的视频数据压缩到 2 Mbits 左右。
    format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
    // 设置传递而来的fps参数
    format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
    // 设置每两秒一个关键帧
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

    format.setInteger(
        MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    );


    // 创建一个专门用于 H.264 编码的 MediaCodec 实例
    mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
    mediaCodec.configure(
        format,
        null,
        null,
        MediaCodec.CONFIGURE_FLAG_ENCODE
    );
    // 当摄像头或者OpenGL ES将图像数据（通常是YUV420格式）渲染到这个Surface上时，
    // MediaCodec 会自动获取这些数据并启动硬件加速的编码过程。
    inputSurface = mediaCodec.createInputSurface();
    mediaCodec.start();
    startDrainThread();
    Log.i(TAG, "VideoEncode prepared: " + width + "x" + height);

    // 在 OnEncodeFrame 中的 packetizer.consume(data); 会在分割完成每个NALU 之后调用回调函数,
    // 这个回调函数会调用 rtpSession 中的 sendNalu 方法
    packetizer.setCallback((nalu, type, isKeyFrame) -> {
      Log.d("RTP", "NALU type=" + type + " size=" + nalu.length);
      rtpSession.sendNalu(nalu, type, isKeyFrame);
    });

    return inputSurface;
  }

  private void startDrainThread() {
    running = true;
    drainThread = new Thread(this::drainEncoder, "VideoEncoderDrain");
    drainThread.start();
  }

  // drainEncoder 的核心任务就是持续地从编码器（Encoder）的“输出管道”中将已经编码完成的数据
  // （即H.264视频帧）给“抽”出来，然后进行下一步处理。
  private void drainEncoder() {
    // 存储当前输出缓冲区的元数据信息，不存实际编码数据
    // 存储的元数据包括
    //offset: int 类型，表示有效数据在 ByteBuffer 中的起始位置（偏移量）。通常为 0。
    //size: int 类型，表示 ByteBuffer 中有效数据的字节数。
    //presentationTimeUs: long 类型，表示这个数据帧的呈现时间戳（PTS），单位是微秒。这个时间戳非常重要，用于在播放时正确地同步视频帧。
    //  flags: int 类型，是一个位掩码，提供关于这个缓冲区的特殊信息。常见的值有：
    //  MediaCodec.BUFFER_FLAG_KEY_FRAME: 表示当前帧是一个关键帧（I-frame）。关键帧是解码器可以独立解码的完整图像帧。
    //  MediaCodec.BUFFER_FLAG_CODEC_CONFIG: 表示这个缓冲区包含了编解码器的配置数据（比如 H.264 中的 SPS/PPS），而不是实际的媒体数据。这些数据需要在媒体流的开头发送。
    //  MediaCodec.BUFFER_FLAG_END_OF_STREAM: 表示已经到达流的末尾。
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    while (running) {
      // 检查 mediaCodec 的输出队列缓存, 如果有数据, 那么将数据填充到bufferInfo(10毫秒超时)
      //  index 就是这个数据帧所在的缓冲区的编号。
      int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10_000);

      if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat newFormat = mediaCodec.getOutputFormat();
        Log.i(TAG, "Output format changed: " + newFormat);
      } else if (index >= 0) {
        // index >= 0: 这是最常见的情况，表示成功获取到了一个编码完成的数据帧的索引。
        // 存储编码器输出的原始二进制数据，即 H.264 裸流的 NALU 数据（包含 SPS/PPS、I/P/B 帧切片）
        ByteBuffer encodedData = mediaCodec.getOutputBuffer(index);

        // 根据 bufferInfo 对齐 encodedData
        if (encodedData != null && bufferInfo.size > 0) {
          encodedData.position(bufferInfo.offset);
          encodedData.limit(bufferInfo.offset + bufferInfo.size);

          // 🚩 这里就是“编码完成的数据出口
          packetizer.consume(encodedData);

        }
        // 归还这个索引指向的输出缓冲区
        mediaCodec.releaseOutputBuffer(index, false);
      }
    }
  }


  public void stop() {
    running = false;

    if (drainThread != null) {
      try {
        drainThread.join();
      } catch (InterruptedException ignored) {
      }
      drainThread = null;
    }

    if (mediaCodec != null) {
      mediaCodec.stop();
      mediaCodec.release();
      mediaCodec = null;
    }

    inputSurface = null;
    Log.i(TAG, "VideoEncoder stopped");
  }
}
