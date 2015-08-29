package com.example.coolweathersimple.util;

public interface HttpCallbackListener {

	void onFinish(String response);

	void onError(Exception e);

}
