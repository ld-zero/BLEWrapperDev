package ai.ldzero.blewrapperdev.ble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import ai.ldzero.blewrapperdev.ble.taskqueue.TaskExecutor;
import ai.ldzero.blewrapperdev.ble.taskqueue.WrappedAsyncTask;


/**
 * 蓝牙基本操作封装类
 * BLEWrapper工作时使用任务队列把所有蓝牙操作串成同步操作执行
 *
 * Created on 2017/7/6.
 *
 * @author otto
 */

public class BLEWrapper {

    private final String LOG_TAG = this.getClass().getSimpleName();

    private static BLEWrapper mInstance;

    private BLEScanner mBLEScanner;

    private Map<String, BLEDeviceOperator> mDeviceMap;

    private TaskExecutor mTaskExecutor;

    /* 标志Wrapper是否已被初始化 */
    private boolean mIsInit = false;

    /* 任务队列长度 */
    private final int TASK_QUEUE_SIZE = 10;

    /* 蓝牙适配器 */
    private BluetoothAdapter mBluetoothAdapter;

    public static BLEWrapper getInstance() {
        if (mInstance == null) {
            synchronized (BLEWrapper.class) {
                if (mInstance == null) {
                    mInstance = new BLEWrapper();
                }
            }
        }
        return mInstance;
    }

    private BLEWrapper() {
        mDeviceMap = new HashMap<>();
        mTaskExecutor = new TaskExecutor(TASK_QUEUE_SIZE);
    }

    /**
     * 开始工作
     *
     */
    public void start(Context context) {
        Log.d(LOG_TAG, "wrapper start");
        mBluetoothAdapter = ((BluetoothManager) context.getApplicationContext()
                .getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (mBluetoothAdapter == null) {
            //TODO: Throw Exception
        }
        mBLEScanner = new BLEScanner(mBluetoothAdapter);
        mTaskExecutor.startWorking();
        mIsInit = true;
    }

    /**
     * 返回Wrapper是否已被初始化
     *
     * @return 是否已被初始化
     */
    public boolean isInit() {
        return mIsInit;
    }

    /**
     * 开启蓝牙
     *
     * @param activity activity
     * @param requestCode 请求码，用于在onActivityResult中处理结果
     */
    public void enableBluetooth(Activity activity, int requestCode) {
        if (!isBluetoothEnable()) {
            Log.d(LOG_TAG, "enable bluetooth");
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(intent, requestCode);
        }
    }

    /**
     * 判断蓝牙是否开启
     *
     * @return 蓝牙是否开启
     */
    public boolean isBluetoothEnable() {
        return mBluetoothAdapter.isEnabled();
    }

    /**
     * 搜索设备
     *
     * @param filterRepeatDevice 是否过滤相同的设备
     * @param timeoutMillis 扫描超时时间，单位毫秒
     */
    public void startScan(boolean filterRepeatDevice, long timeoutMillis) {
        mBLEScanner.startScan(filterRepeatDevice, timeoutMillis);
    }

    /**
     * 停止搜索设备
     *
     */
    public void stopScan() {
        mBLEScanner.stopScan();
    }

    /**
     * 添加连接设备任务到任务队列中
     *
     * @param context context
     * @param mac 设备mac
     * @param timeoutMillis 连接超时时间，单位毫秒
     */
    public void connect(Context context, final String mac, long timeoutMillis) {
        if (!mDeviceMap.containsKey(mac)) {
            mDeviceMap.put(mac, new BLEDeviceOperator(context, mac, mBluetoothAdapter));
        }
        BLEDeviceOperator operator = mDeviceMap.get(mac);
        if (operator == null) {
            return;
        }
        final WrappedAsyncTask task = new ConnectTask(context, mac, timeoutMillis);
        operator.setOnStateListener(new BLEDeviceOperator.OnStateListener() {
            @Override
            public void onConnectComplete(boolean success) {
                if (mOnDeviceStateListener != null) {
                    mOnDeviceStateListener.onConnectComplete(mac, success);
                }
                if (success) {
                    task.finishTask();
                }
            }

            @Override
            public void onServiceDiscover() {
                if (mOnDeviceStateListener != null) {
                    mOnDeviceStateListener.onServiceDiscover(mac);
                }
            }

            @Override
            public void onDisconnect() {
                if (mOnDeviceStateListener != null) {
                    mOnDeviceStateListener.onDisconnect(mac);
                }
            }

            @Override
            public void onClose() {
                if (mOnDeviceStateListener != null) {
                    mOnDeviceStateListener.onClose(mac);
                }
            }
        });
        operator.setOnDataListener(new BLEDeviceOperator.OnDataListener() {
            @Override
            public void onWrite(boolean success) {
                if (mOnDataListener != null) {
                    mOnDataListener.onWrite(mac, success);
                }
            }

            @Override
            public void onRead(boolean success, byte[] data) {
                if (mOnDataListener != null) {
                    mOnDataListener.onRead(mac, success, data);
                }
            }

            @Override
            public void onCharacteristicChanged(UUID characteristicUUID, byte[] data) {
                if (mOnDataListener != null) {
                    mOnDataListener.onCharacteristicChanged(mac, characteristicUUID, data);
                }
            }
        });
        mTaskExecutor.addTask(task);
    }

    /**
     * 连接设备任务
     *
     */
    private class ConnectTask extends WrappedAsyncTask {

        private String mMac;

        private Context mContext;

        private long mTimeoutMillis;

        ConnectTask(Context context, String mac, long timeoutMillis) {
            mMac = mac;
            mContext = context.getApplicationContext();
            mTimeoutMillis = timeoutMillis;
        }

        @Override
        public void _run() {
            BLEDeviceOperator operator = mDeviceMap.get(mMac);
            if (operator == null) {
                finishTask();
                return;
            }
            stopScan();
            operator.connect(mContext, new BLEDeviceOperator.OnResultListener() {
                @Override
                public void onResult(boolean success, String msg) {
                    finishTask();
                }
            }, mTimeoutMillis);
        }
    }

    /**
     * 添加写任务到任务队列中
     *
     * @param mac 设备mac
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param data 写入的数据，长度不超过20字节
     * @param timeoutMillis 超时时间，单位毫秒
     */
    public void write(String mac, UUID serviceUUID, UUID characteristicUUID, byte[] data, long timeoutMillis) {
        if (!mDeviceMap.containsKey(mac)) {
            return;
        }
        BLEDeviceOperator operator = mDeviceMap.get(mac);
        if (operator == null) {
            return;
        }
        // TODO: 判断数据长度
        mTaskExecutor.addTask(new WriteTask(mac, serviceUUID, characteristicUUID, data, timeoutMillis));
    }

    /**
     * 写任务
     */
    private class WriteTask extends WrappedAsyncTask {

        private String mMac;

        private UUID mServiceUUID;

        private UUID mCharacteristicUUID;

        private byte[] data;

        private long mTimeoutMillis;

        WriteTask(String mac, UUID serviceUUID, UUID characteristicUUID, byte[] data, long timeoutMillis) {
            mMac = mac;
            mServiceUUID = serviceUUID;
            mCharacteristicUUID = characteristicUUID;
            this.data = data;
            mTimeoutMillis = timeoutMillis;
        }

        @Override
        public void _run() {
            BLEDeviceOperator operator = mDeviceMap.get(mMac);
            if (operator == null) {
                finishTask();
                return;
            }
            operator.write(mServiceUUID, mCharacteristicUUID, data, new BLEDeviceOperator.OnResultListener() {
                @Override
                public void onResult(boolean success, String msg) {
                    finishTask();
                }
            }, mTimeoutMillis);
        }
    }

    /**
     * 添加读任务到任务队列中
     *
     * @param mac 设备mac
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param timeoutMillis 超时时间，单位毫秒
     */
    public void read(String mac, UUID serviceUUID, UUID characteristicUUID, long timeoutMillis) {
        if (!mDeviceMap.containsKey(mac)) {
            return;
        }
        BLEDeviceOperator operator = mDeviceMap.get(mac);
        if (operator == null) {
            return;
        }
        mTaskExecutor.addTask(new ReadTask(mac, serviceUUID, characteristicUUID, timeoutMillis));
    }

    /**
     * 读任务
     *
     */
    private class ReadTask extends WrappedAsyncTask {

        private String mMac;

        private UUID mServiceUUID;

        private UUID mCharacteristicUUID;

        private long mTimeoutMillis;

        ReadTask(String mac, UUID serviceUUID, UUID characteristicUUID, long timeoutMillis) {
            mMac = mac;
            mServiceUUID = serviceUUID;
            mCharacteristicUUID = characteristicUUID;
            mTimeoutMillis = timeoutMillis;
        }

        @Override
        public void _run() {
            BLEDeviceOperator operator = mDeviceMap.get(mMac);
            if (operator == null) {
                finishTask();
                return;
            }
            operator.read(mServiceUUID, mCharacteristicUUID, new BLEDeviceOperator.OnResultListener() {
                @Override
                public void onResult(boolean success, String msg) {
                    finishTask();
                }
            }, mTimeoutMillis);
        }
    }

    /**
     * 打开设备通知
     *
     * @param mac 设备mac
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param descriptorUUID 描述UUID
     */
    public void openNotification(String mac, UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID) {
        if (!mDeviceMap.containsKey(mac)) {
            return;
        }
        BLEDeviceOperator operator = mDeviceMap.get(mac);
        if (operator == null) {
            return;
        }
        operator.subscribe(serviceUUID, characteristicUUID, descriptorUUID);
    }

    /**
     * 关闭设备通知
     *
     * @param mac 设备mac
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param descriptorUUID 描述UUID
     */
    public void closeNotification(String mac, UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID) {
        if (!mDeviceMap.containsKey(mac)) {
            return;
        }
        BLEDeviceOperator operator = mDeviceMap.get(mac);
        if (operator == null) {
            return;
        }
        operator.unSubscribe(serviceUUID, characteristicUUID, descriptorUUID);
    }

    /**
     * 断开设备连接
     *
     * @param mac 设备mac地址
     */
    public void disconnect(String mac) {
        if (!mDeviceMap.containsKey(mac)) {
            return;
        }
        BLEDeviceOperator operator = mDeviceMap.get(mac);
        if (operator == null) {
            return;
        }
        operator.disconnect();
    }

    /**
     * 停止工作
     *
     */
    public void stop() {
        Log.d(LOG_TAG, "BLEWrapper stop working");
        mBLEScanner.stop();
        for (String mac : mDeviceMap.keySet()) {
            mDeviceMap.get(mac).stop();
        }
        mDeviceMap.clear();
        mTaskExecutor.stopWorking();
        release();
        mIsInit = false;
    }

    /**
     * 释放资源
     *
     */
    private void release() {
        Log.d(LOG_TAG, "BLEWrapper release");
        mBLEScanner = null;
        mDeviceMap = null;
        mInstance = null;
        mTaskExecutor = null;
    }

    /* --------------- Listener and setter --------------- */
    public void setOnScanListener(OnScanListener onScanListener) {
        mBLEScanner.setOnScanListener(onScanListener);
    }

    public interface OnScanListener {
        void onDeviceScan(BluetoothDevice device);

        void onTimeout();
    }

    private OnDeviceStateListener mOnDeviceStateListener;

    public void setOnDeviceStateListener(OnDeviceStateListener onDeviceStateListener) {
        mOnDeviceStateListener = onDeviceStateListener;
    }

    public interface OnDeviceStateListener {
        void onConnectComplete(String mac, boolean success);

        void onServiceDiscover(String mac);

        void onDisconnect(String mac);

        void onClose(String mac);
    }

    private OnDataListener mOnDataListener;

    public void setOnDataListener(OnDataListener onDataListener) {
        mOnDataListener = onDataListener;
    }

    public interface OnDataListener {
        void onRead(String mac, boolean success, byte[] data);

        void onWrite(String mac, boolean success);

        void onCharacteristicChanged(String mac, UUID characteristicUUID, byte[] data);
    }
}
