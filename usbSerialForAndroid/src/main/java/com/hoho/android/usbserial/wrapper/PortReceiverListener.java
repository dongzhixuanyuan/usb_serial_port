package com.hoho.android.usbserial.wrapper;

public interface PortReceiverListener {
    void onDataReceive(String content);
    void onError(Exception e);
}