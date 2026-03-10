package com.marine.secretcamera.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class OkHttpManager {
  private static final OkHttpClient instance = new OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .writeTimeout(5, TimeUnit.SECONDS)
      .pingInterval(30, TimeUnit.SECONDS)
      .build();

  private OkHttpManager() {}

  public static OkHttpClient getInstance() {
    return instance;
  }

}
