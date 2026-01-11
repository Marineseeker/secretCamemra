package com.marine.secretcamera.rtp;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
//============= RTP 协议报头=======
//  Version 表示 RTP 协议的版本，目前版本为 2。
//  P (Padding) 表示 RT(D)P 包末尾是否有 padding bytes，且 padding bytes 的最后一个 byte 表示 bytes 的数量。Padding 可以被用来填充数据块，比如加密算法可能会用到。
//  X (Extension) 表示是否有头部扩展，头部扩展可以用于存储信息，比如视频旋转角度。
//  CC (CSRC count) 表示红色部分的 CSRC（参见下文）数量，显然最多只能有 15 个 CSRC。
//  M (Marker) 表示当前数据是否与应用程序有某种特殊的相关性。比如传输的是一些私有数据，或者数据中的某些标志位具有特殊的作用。
//  PT (Payload type) 表示 payload 的数据类型，音视频的默认映射格式可参见 RFC 3551。
//  Sequence number 是递增的序列号，用于标记每一个被发送的 RT(D)P 包。接收方可以根据序列号按顺序重新组包，以及识别是否有丢包。序列号的初始值应当是随机的（不可预测的），从而增加明文攻击的难度。
//  Timestamp 即时间戳，接收方根据其来回放音视频。时间戳的间隔由传输的数据类型（或具体的应用场景）确定，比如音频通常以 125µs（8kHz）的时钟频率进行采样，而视频则以 90kHz 的时钟频率采样。这里时间戳的初始值也是随机选取的，是一种相对时间戳。
//  SSRC (Synchronization source) 即同步源标识符。相同 RTP 会话中的 SSRC 是唯一的，且生成的 SSRC 也需要保持随机。尽管多个源选中同一个标识符的概率很低，但具体实现时仍然需要这种情况发生，即避免碰撞。
//  CSRC (Contributing source) 在 MCU 混流时使用，表示混流出的新的音视频流的 SSRC 是由哪些源 SSRC 贡献的。根据上述 CC 得知，我们最多可以同时混 15 路音视频流。
//  Extension header 即头部扩展，包含了音视频的一些额外信息，比如视频旋转角度。
public class RtpSession {

  // ===== RTP 固定参数 =====
  private static final int RTP_VERSION = 2;
  private static final int PAYLOAD_TYPE_H264 = 96;
  private static final int CLOCK_RATE = 90000;

  // ===== RTP 状态 =====
  private int sequenceNumber;

  //  timestamp 指的是 RTP时间戳（RTP Timestamp）。
  //  它标记了RTP数据包中第一帧数据的采样时刻
  private long timestamp;

  //  SSRC 是 Synchronization Source（同步源） 的缩写。
  //  在RTP（实时传输协议）中，它是一个32位的数字标识符，用来唯一地识别一个媒体流的来源。
  private int ssrc;

  // ======视频参数=====
  private int fps;

  //  timestampStep (时间戳增量) 是 下一帧RTP时间戳相对于上一帧应该增加的值。
  //  它是一个预先计算好的步长。
  private long timestampStep;

  // ===== 网络 =====
  private DatagramSocket socket;
  private InetAddress remoteAddress;
  private int remotePort;


  // ===== MTU设置 =====
  // 这定义了你的应用认为的网络路径上，一个UDP数据包所能承载的最大尺寸。这是一个经验值
  private static final int MTU = 1400;

  // 一个标准的RTP包总是在其数据负载（Payload）前包含一个12字节的头部，
  // 这个头部包含了版本、序列号、时间戳、SSRC等关键信息
  private static final int RTP_HEADER_SIZE = 12;

  // FU-A格式。这个格式要求在RTP头之后、真正的视频数据片段之前，增加两个字节的FU-A头部：
  private static final int FU_A_HEADER_SIZE = 2;

  // MAX_FU_PAYLOAD 计算并定义了在单个FU-A分片RTP包中，
  // 能够容纳的最大视频数据片段 (Payload) 的大小。
  // 即 1400 - 12 - 2
  private static final int MAX_FU_PAYLOAD =
      MTU - RTP_HEADER_SIZE - FU_A_HEADER_SIZE;

  // 单 NALU（不分片）最大 payload
  private static final int MAX_SINGLE_NALU_SIZE =
      MTU - RTP_HEADER_SIZE;


  //  start() 只干三件事：
  //  初始化 RTP 状态
  //  初始化 socket
  //  准备好“可以发包”的条件
  public void start(String ip, int port, int fps) throws Exception {
    this.fps = fps;
    this.timestampStep = CLOCK_RATE / fps;

    // 初始化序列号与时间戳
    this.sequenceNumber = 0;
    this.timestamp = 0;
    this.ssrc = new Random().nextInt();

    this.remoteAddress = InetAddress.getByName(ip);
    this.remotePort = port;

    this.socket = new DatagramSocket();
  }

  //  当一个 NALU > MTU（通常 1200~1400 字节）
  //  单 RTP 包放不下
  //  必须拆成多个 RTP 包
  //  使用 FU-A（Fragmentation Unit - Type 28）

  public void sendNalu(byte[] nalu, int type, boolean isKeyFrame) {
    if (nalu.length <= MAX_SINGLE_NALU_SIZE) {
      sendSingleNalu(nalu);
    } else {
      sendFuANalu(nalu);
    }
  }

  private void sendSingleNalu(byte[] nalu) {
    // 1. 构造 RTP Header（12 bytes）
    int payloadSize = nalu.length;
    int rtpPacketSize = 12 + payloadSize;

    byte[] packet = new byte[rtpPacketSize];
    int offset = 0;

    // 2. 拼 payload（NALU 去掉 0x00000001）
    // Byte 0: V=2, P=0, X=0, CC=0
    packet[offset++] = (byte) (0x80);

    // Byte 1: M=1 (single NALU), PT=96
    // 异或用于保持第一位始终为一
    packet[offset++] = (byte) (0x80 | PAYLOAD_TYPE_H264);

    // Sequence Number (16 bits)
    packet[offset++] = (byte) ((sequenceNumber >> 8) & 0xFF);
    packet[offset++] = (byte) (sequenceNumber & 0xFF);

    // Timestamp (32 bits)
    packet[offset++] = (byte) ((timestamp >> 24) & 0xFF);
    packet[offset++] = (byte) ((timestamp >> 16) & 0xFF);
    packet[offset++] = (byte) ((timestamp >> 8) & 0xFF);
    packet[offset++] = (byte) (timestamp & 0xFF);

    // SSRC (32 bits)
    packet[offset++] = (byte) ((ssrc >> 24) & 0xFF);
    packet[offset++] = (byte) ((ssrc >> 16) & 0xFF);
    packet[offset++] = (byte) ((ssrc >> 8) & 0xFF);
    packet[offset++] = (byte) (ssrc & 0xFF);

    // ===== RTP Payload (NALU) =====
    System.arraycopy(nalu, 0, packet, offset, payloadSize);

    // ===== UDP Send =====
    DatagramPacket udpPacket =
        new DatagramPacket(packet, packet.length, remoteAddress, remotePort);
    try {
      socket.send(udpPacket);
    } catch (IOException e) {
      Log.e("RtpSession", "failed to send Single Packet");
      throw new RuntimeException(e);
    }
    // ===== 更新 RTP 状态 =====
    sequenceNumber++;
    timestamp += timestampStep;
  }

  // 这个方法的作用是接收一个超过MTU大小的NALU，将其“切片”，
  // 然后将每个“切片”用RTP和FU-A头部包装起来，通过UDP发送出去。
  private void sendFuANalu(byte[] nalu) {
    //      RTP Header (12 bytes)
    //+-----------------------------------+
    //|   FU Indicator (1 byte)           |
    //+-----------------------------------+
    //|   FU Header (1 byte)              |
    //+-----------------------------------+
    //|   NALU Payload Data (N-bytes)     |
    //+-----------------------------------+

      // ---- 原始 NALU header ----
      byte nalHeader = nalu[0];
      // & 0x1F: 0x1F 的二进制是 0001 1111。通过“与”运算，可以提取出 nalHeader 的低5位，
      // 这正是原始NALU的类型（例如，I帧是5，P帧是1）。这个 nalType 将被用在下面的FU Header中。
      int nalType = nalHeader & 0x1F;
      int nri = nalHeader & 0x60;
      int forbidden = nalHeader & 0x80;

      // ---- FU Indicator ----
      byte fuIndicator = (byte) (forbidden | nri | 28);

      int payloadOffset = 1; // 跳过原始 NALU header
      int payloadRemaining = nalu.length - 1;

      boolean isFirst = true;

      while (payloadRemaining > 0) {
        int chunkSize = Math.min(payloadRemaining, MAX_FU_PAYLOAD);
        boolean isLast = payloadRemaining - chunkSize == 0;

        byte fuHeader = (byte) nalType;
        // fuHeader |= 0x80: 如果是第一个分片 (isFirst为true)，
        // 就通过或运算将 FU Header 的最高位置为1。
        // 这个就是Start Bit (S=1)。0x80的二进制是1000 0000。
        if (isFirst) fuHeader |= 0x80; // S
        // fuHeader |= 0x40: 如果是最后一个分片 (isLast为true)，就将第6位置为1。
        // 这个就是End Bit (E=1)。0x40的二进制是0100 0000。
        if (isLast) fuHeader |= 0x40;  // E

        int packetSize =
            RTP_HEADER_SIZE + FU_A_HEADER_SIZE + chunkSize;

        byte[] packet = new byte[packetSize];
        int offset = 0;

        // ===== RTP Header =====
        packet[offset++] = (byte) 0x80;
        packet[offset++] = (byte)
            ((isLast ? 0x80 : 0x00) | PAYLOAD_TYPE_H264);

        packet[offset++] = (byte) (sequenceNumber >> 8);
        packet[offset++] = (byte) sequenceNumber;

        packet[offset++] = (byte) (timestamp >> 24);
        packet[offset++] = (byte) (timestamp >> 16);
        packet[offset++] = (byte) (timestamp >> 8);
        packet[offset++] = (byte) timestamp;

        packet[offset++] = (byte) (ssrc >> 24);
        packet[offset++] = (byte) (ssrc >> 16);
        packet[offset++] = (byte) (ssrc >> 8);
        packet[offset++] = (byte) ssrc;

        // ===== FU-A =====
        packet[offset++] = fuIndicator;
        packet[offset++] = fuHeader;

        System.arraycopy(
            nalu,
            payloadOffset,
            packet,
            offset,
            chunkSize
        );

        // ===== UDP Send =====
        DatagramPacket udpPacket =
            new DatagramPacket(packet, packet.length, remoteAddress, remotePort);
        try {
          socket.send(udpPacket);
        } catch (IOException e) {
          Log.e("RtpSession", "failed to send FU-A Packet");
          throw new RuntimeException(e);
        }

        sequenceNumber++;

        payloadOffset += chunkSize;
        payloadRemaining -= chunkSize;
        isFirst = false;
      }

      // 一整个 NALU 结束后才推进 timestamp
      timestamp += timestampStep;
  }
  public void stop() {
    if (socket != null && !socket.isClosed()) {
      socket.close();
      socket = null;
    }
  }
}
