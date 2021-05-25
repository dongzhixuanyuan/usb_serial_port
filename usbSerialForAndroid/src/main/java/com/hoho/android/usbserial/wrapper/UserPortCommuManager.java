package com.hoho.android.usbserial.wrapper;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.BuildConfig;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;


/**
 * 串口操作管理类：
 * 1、先通过[UserPortCommuManager.getInstance(Context context)]拿到单例，这个Context就是android程序的一个上下文环境。
 * 2、调用[openAndConnect(baudRate)打开端口，参数是波特率]，打开成功返回true，否则返回false.
 * 3、发送数据调用[write(hexStr)],参数必须是十六进制字符串。发送成功返回true，否则返回false.
 * 4、接收串口设备发送的信息，通过[UserPortCommuManager.setListener()]，传一个PortReceiverListener的接口实例进来就行了。
 * [onDataReceive(data)],回调的结果是十六进制的字符串。
 * 5、断开串口连接[disconnect()].
 */
public class UserPortCommuManager implements SerialInputOutputManager.Listener {

    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final String TAG = "UserPortCommuManager" ;

    public static UserPortCommuManager instance;
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.LIBRARY_PACKAGE_NAME + ".GRANT_USB";

    private Context context;
    private boolean isOpen;

    public String vendor; //设备vendor，用来作为目标串口的标识ID
    private  SerialInputOutputManager usbIoManager;
    private UsbSerialPort mPort;
    private BroadcastReceiver broadcastReceiver;
    private UsbPermission usbPermission = UsbPermission.Unknown;

    private PortReceiverListener listener;

    /**
     * @param listener 设置接收串口设备信息的回调。
     */
    public void setListener(PortReceiverListener listener) {
        this.listener = listener;
    }

    private int bauteRadio = 0 ;
    private UserPortCommuManager(Context context, final String vendor) {
        this.context = context;
        this.vendor = vendor;
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(INTENT_ACTION_GRANT_USB)) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    openAndConnect(bauteRadio == 0 ? 9600 : bauteRadio);
                }
            }
        };
        this.context.registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
    }

    public static synchronized UserPortCommuManager getInstance(Context context,String vendor) {
        if (instance == null) {
            instance = new UserPortCommuManager(context,vendor);
        }
        return instance;
    }


    /**
     * @param baudRate 波特率
     * @return
     */
    public boolean openAndConnect(int baudRate)  {
        if (isOpen) {
            Log.e(TAG,"端口已经打开了");
            return false;
        }
        this.bauteRadio = baudRate;
        // Find all available drivers from attached devices.
        try {
            UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
            if (availableDrivers.isEmpty()) {
                return false;
            }

            // Open a connection to the first available driver.
            UsbSerialDriver driver = null;
            for (int i = 0; i < availableDrivers.size(); i++) {
                UsbSerialDriver usbSerialDriver = availableDrivers.get(i);
                int vendorId = usbSerialDriver.getDevice().getVendorId();
                String vendorStr = String.format("%04X", vendorId);
                if (vendorStr.equals(vendor)) {
                    driver = usbSerialDriver;
                }
            }
            if (driver == null) {
                Log.e(TAG,"未找到指定设备");
                return false;
            }
            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
            if(connection == null && usbPermission == UsbPermission.Unknown && !manager.hasPermission(driver.getDevice())) {
                usbPermission = UsbPermission.Requested;
                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
                manager.requestPermission(driver.getDevice(), usbPermissionIntent);
                Log.e(TAG,"没有权限");
                return false;
            }
            // Most devices have just one port (port 0)
            mPort = driver.getPorts().get(0);
            mPort.open(connection);
            mPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            isOpen = true;
            usbIoManager = new SerialInputOutputManager(mPort, this);
            Executors.newSingleThreadExecutor().submit(usbIoManager);
            return true;
        }catch (IOException e){
            Log.e(TAG,"打开失败，exception:"+e.getMessage());
            return false;
        }
    }

    /**
     * @param hexStr 写入的内容，十六进制字符串
     * @return
     */
    public  boolean write(String hexStr) {
        if (!isOpen) {
            return false;
        }
        byte[] data = HexDump.HexToByteArr(hexStr);

        try {
            mPort.write(data, WRITE_WAIT_MILLIS);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * 断开串口连接
     */
    private void disconnect() {
        isOpen = false;
        if (usbIoManager != null) {
            usbIoManager.stop();
            usbIoManager = null;
        }
        if (mPort != null) {
            try {
                mPort.close();
            } catch (IOException ignored) {
            }
        }
        mPort = null;
    }


    /**
     * @param data 收到串口设置的信息的回调。
     */
    @Override
    public void onNewData(byte[] data) {
        if (listener!=null) {
            listener.onDataReceive(HexDump.toHexString(data));
        }
    }

    @Override
    public void onRunError(Exception e) {
        if (listener!=null) {
            listener.onError(e);
        }
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

}



 enum UsbPermission { Unknown, Requested, Granted, Denied }



