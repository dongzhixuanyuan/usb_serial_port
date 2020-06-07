package com.hoho.android.usbserial.examples;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.hoho.android.usbserial.wrapper.PortReceiverListener;
import com.hoho.android.usbserial.wrapper.UserPortCommuManager;

public class Main2Activity extends AppCompatActivity implements PortReceiverListener {

    private static final String TAG = "Main2Activity";

    private EditText outputText;
    private TextView receiveText;
    private UserPortCommuManager mUserPortCommuManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        outputText = findViewById(R.id.outputText);
        receiveText = findViewById(R.id.receive_text);
    }


    public void openPort(View view) {
        mUserPortCommuManager = UserPortCommuManager.getInstance(getApplicationContext());
        boolean success = mUserPortCommuManager.openAndConnect(9600);
        mUserPortCommuManager.setListener(this);
        Log.d(TAG, "openPort: success:" + success);
    }

    @Override
    public void onDataReceive(String content) {
        receiveText.setText(content);
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "onError: " + e.getMessage());
    }

    public void send(View view) {
        String command = outputText.getText().toString().trim();
        boolean success = mUserPortCommuManager.write(command);
        Log.e(TAG, "发送失败: " + success);
    }
}
