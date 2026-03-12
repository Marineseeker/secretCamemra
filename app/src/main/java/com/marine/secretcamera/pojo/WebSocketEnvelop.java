package com.marine.secretcamera.pojo;

import lombok.Data;

/**
 * 推送请求主实体类
 * 对应整个push_request消息结构
 */
@Data
public class WebSocketEnvelop {
  private String type;
  private PushRequestData data;
  @Data
  public static class PushRequestData {
    private String targetDeviceId;
    private String fromDeviceId;
    private Long expireAt;
  }
}