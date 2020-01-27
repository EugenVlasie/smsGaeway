package com.webbysoft.smsgateway;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiInterface {
    @POST("/resend")
    Call<Void> resendSms(@Header("Content-Type") String content_type, @Header("x-sms-key") String authorization,
                         @Body JsonObject requestBody);
}
