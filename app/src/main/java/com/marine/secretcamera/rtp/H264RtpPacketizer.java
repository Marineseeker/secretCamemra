package com.marine.secretcamera.rtp;

import java.nio.ByteBuffer;

//  接收 H.264 Annex-B ByteBuffer
//  拆成一个个完整 NALU
//  告诉你：
//  NALU 类型
//  NALU 长度
//  是否 IDR
public class H264RtpPacketizer {
  public interface NaluCallback {
    void onNalu(byte[] nalu, int type, boolean isKeyFrame);
  }
  private NaluCallback callback;

  public void setCallback(NaluCallback callback) {
    this.callback = callback;
  }
  public void consume(ByteBuffer data) {
    // 1. 复制ByteBuffer并转化为 byte[]
    ByteBuffer dup = data.duplicate();
    byte[] raw = new byte[dup.remaining()];
    dup.get(raw);

    // 2. 初始化偏移量, 开始扫描
    int start = 0;
    // 3. 循环扫描, 寻找并处理所有 NALU
    while (true) {
      // 3.1 寻找下一个NALU的起始码
      int nalStart = findStartCode(raw, start);
      if (nalStart < 0) break; // 如果找不到了, 说明流已经处理完毕

      // 3.2 确定NALU内容的真正起始位置
      int nalHeader = nalStart + startCodeLength(raw, nalStart);

      // 3.3 寻找下一个 NALU 的起始位置
      int nextStart = findStartCode(raw, nalHeader);

      // 如果没有下一个, 则当前的 NALU 的结尾就是整个数据块的结尾
      int nalEnd = (nextStart >= 0) ? nextStart : raw.length;

      // 3.4 计算 NALU 的实际大小
      int nalSize = nalEnd - nalHeader;
      // 3.5 如果大小有效, 则进行处理
      if (nalSize > 0) {
        // 3.5.1 复制NALU数据到字节数组
        byte[] nalu = new byte[nalSize];
        System.arraycopy(raw, nalHeader, nalu, 0, nalSize);

        // 3.5.2 解析NALU类型是否为关键帧
        int type = nalu[0] & 0x1F; // NALU Header 的后五位表示类型
        boolean isKey = (type == 5); // 类型 5 表示 IDR 关键帧

        // 3.5.3 通过回调函数将 NALU 数据与信息传出
        if (callback != null) {
          callback.onNalu(nalu, type, isKey);
        }
      }
      // 4. 更新下一次的起始偏移量
      start = nalEnd;
    }
  }

  private int findStartCode(byte[] data, int offset) {
    for (int i = offset; i < data.length - 3; i++) {
      if (data[i] == 0 && data[i+1] == 0) {
        if (data[i+2] == 1) return i;
        if (data[i+2] == 0 && data[i+3] == 1) return i;
      }
    }
    return -1;
  }

  private int startCodeLength(byte[] data, int index) {
    return (data[index+2] == 1) ? 3 : 4;
  }
}
