package ai.ldzero.blewrapperdev.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * 封装扫描BLE设备相关操作
 *
 * Created on 2017/7/20.
 *
 * @author ldzero
 */

class BLEScanner {

    private final String LOG_TAG = this.getClass().getSimpleName();

    private BluetoothAdapter mBluetoothAdapter;

    /* 搜索到重复设备是否进行过滤 */
    private boolean mFilterRepeatDevice;

    /* 记录搜索到到设备，用于过滤重复设备 */
    private Set<String> mScanDeviceMacSet;

    private HandlerThread mHandleScanResultThread;

    private Handler mHandler;

    private final int MSG_HANDLE_SCAN_RESULT = 100;
    private final int MSG_SCAN_TIMEOUT = 101;

    private BluetoothAdapter.LeScanCallback mScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            // onLeScan回调中只做尽量少的工作，具体逻辑放在HandlerThread完成
            Message message = Message.obtain();
            message.what = MSG_HANDLE_SCAN_RESULT;
            Bundle bundle = new Bundle();
            bundle.putParcelable("BluetoothDevice", device);
            message.setData(bundle);
            mHandler.sendMessage(message);
        }
    };

    BLEScanner(BluetoothAdapter bluetoothAdapter) {
        mScanDeviceMacSet = new HashSet<>();
        mBluetoothAdapter = bluetoothAdapter;
        mHandleScanResultThread = new HandlerThread("HandleScanResultThread");
        mHandleScanResultThread.start();
        mHandler = new Handler(mHandleScanResultThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_HANDLE_SCAN_RESULT:
                        BluetoothDevice device = msg.getData().getParcelable("BluetoothDevice");
                        handleScanResult(device);
                        break;
                    case MSG_SCAN_TIMEOUT:
                        Log.d(LOG_TAG, "scan timeout");
                        if (mOnScanListener != null) {
                            mOnScanListener.onTimeout();
                        }
                        stopScan();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    /**
     * 处理扫描设备结果
     *
     * @param bluetoothDevice 设备
     */
    private void handleScanResult(BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null || bluetoothDevice.getAddress() == null) {
            return;
        }
        if (mFilterRepeatDevice) {
            for (String mac: mScanDeviceMacSet) {
                if (bluetoothDevice.getAddress().equals(mac)) {
                    return;
                }
            }
        }
        Log.d(LOG_TAG, "Search device " + bluetoothDevice.getName() + " " + bluetoothDevice.getAddress());
        if (mOnScanListener != null) {
            mOnScanListener.onDeviceScan(bluetoothDevice);
        }
        mScanDeviceMacSet.add(bluetoothDevice.getAddress());
    }

    /**
     * 开始搜索
     *
     * @param filterRepeatDevice 是否过滤相同设备
     * @param timeoutMillis 超时时间，单位毫秒
     */
    void startScan(boolean filterRepeatDevice, long timeoutMillis) {
        stopScan();
        Log.d(LOG_TAG, "Start scan with timeout " + timeoutMillis + ", filterRepeatDevice = " + filterRepeatDevice);
        mFilterRepeatDevice = filterRepeatDevice;
        mScanDeviceMacSet.clear();
        mBluetoothAdapter.startLeScan(mScanCallback);
        mHandler.sendEmptyMessageDelayed(MSG_SCAN_TIMEOUT, timeoutMillis);
    }

    /**
     * 停止搜索
     *
     */
    void stopScan() {
        Log.d(LOG_TAG, "Stop scan");
        mBluetoothAdapter.stopLeScan(mScanCallback);
    }

    /**
     * 停止工作
     *
     */
    void stop() {
        Log.d(LOG_TAG, "BLEScanner stop working");
        stopScan();
        mHandler.removeMessages(MSG_HANDLE_SCAN_RESULT);
        mHandler.removeMessages(MSG_SCAN_TIMEOUT);
        mHandleScanResultThread.quit();
        release();
    }

    /**
     * 释放资源
     *
     */
    private void release() {
        Log.d(LOG_TAG, "BLEScanner release resources");
        mScanDeviceMacSet = null;
        mHandler = null;
        mHandleScanResultThread = null;
        mBluetoothAdapter = null;
    }

    /* -------------- Listener and setter -------------- */
    private BLEWrapper.OnScanListener mOnScanListener;

    void setOnScanListener(BLEWrapper.OnScanListener onScanListener) {
        mOnScanListener = onScanListener;
    }
}
