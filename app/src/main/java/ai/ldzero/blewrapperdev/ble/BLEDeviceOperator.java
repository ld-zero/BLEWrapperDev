package ai.ldzero.blewrapperdev.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.UUID;

import ai.ldzero.blewrapperdev.ble.utils.LogUtils;


/**
 * 封装了蓝牙设备基本操作
 *
 * Created on 2017/7/20.
 *
 * @author ldzero
 */

class BLEDeviceOperator {

    private final String LOG_TAG = this.getClass().getSimpleName();

    private String mMac;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothGatt mGatt;

    private Handler mHandler;

    private boolean mIsConnect = false;

    private final int MSG_CONN_TIMEOUT = 200;
    private final int MSG_WRITE_TIMEOUT = 201;
    private final int MSG_READ_TIMEOUT = 202;

    // TODO: 增加动作编号
    // TODO: 读写操作超时，resultListener被置空，新的读写操作进来，设置了resultListener，若此时上次操作的回调回来了，会被当作本次操作的结果回调给外部

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(LOG_TAG, mMac + " conn state changed, status = " + status + ", newState = " + newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothGatt.STATE_CONNECTED:
                        mIsConnect = true;
                        // 已与设备建立连接
                        Log.d(LOG_TAG, mMac + " connect successfully");
                        if (mOnStateListener != null) {
                            mOnStateListener.onConnectComplete(true);
                        }
                        if (mOnConnResultListener != null) {
                            mOnConnResultListener.onResult(true, "success");
                            mOnConnResultListener = null;
                        }
                        mHandler.removeMessages(MSG_CONN_TIMEOUT);
                        // 在主线程发现设备服务
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mGatt.discoverServices();
                            }
                        });
                        break;
                    case BluetoothGatt.STATE_DISCONNECTED:
                        Log.d(LOG_TAG, mMac + " device disconnected");
                        mIsConnect = false;
                        // 已与设备断开连接
                        if (mOnStateListener != null) {
                            mOnStateListener.onDisconnect();
                        }
                        close();
                        break;
                    default:
                        break;
                }
            } else {
                // 连接不成功，关闭连接
                // 若设备主动断开蓝牙，也会回到这个回调里
                Log.d(LOG_TAG, mMac + " connect failed");
                mIsConnect = false;
                mHandler.removeMessages(MSG_CONN_TIMEOUT);
                if (mOnStateListener != null) {
                    mOnStateListener.onConnectComplete(false);
                }
                if (mOnConnResultListener != null) {
                    mOnConnResultListener.onResult(false, "failed");
                    mOnConnResultListener = null;
                }
                close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, mMac + " service discovered success");
                // 发现服务成功
                mOnStateListener.onServiceDiscover();
            } else {
                Log.d(LOG_TAG, mMac + " service discovered failed");
                // 发现服务失败，关闭连接
                disconnect();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            mHandler.removeMessages(MSG_READ_TIMEOUT);
            boolean success = status == BluetoothGatt.GATT_SUCCESS;
            Log.d(LOG_TAG, mMac + " read " + (success ? "success" : "failed") + ", data = " +
                    LogUtils.byteArray2Str(characteristic == null ? null : characteristic.getValue()));
            if (mOnDataListener != null) {
                mOnDataListener.onRead(success, characteristic == null ? null : characteristic.getValue());
            }
            if (mOnReadResultListener != null) {
                mOnReadResultListener.onResult(success, success ? "success" : "failed");
                mOnReadResultListener = null;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            mHandler.removeMessages(MSG_WRITE_TIMEOUT);
            boolean success = status == BluetoothGatt.GATT_SUCCESS;
            Log.d(LOG_TAG, mMac + " write " + (success ? "success" : "failed") + ", data = " +
                    LogUtils.byteArray2Str(characteristic == null ? null : characteristic.getValue()));
            if (mOnDataListener != null) {
                mOnDataListener.onWrite(success);
            }
            if (mOnWriteResultListener != null) {
                mOnWriteResultListener.onResult(success, success ? "success" : "failed");
                mOnWriteResultListener = null;
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(LOG_TAG, mMac + " characteristic changed, data = " + LogUtils.byteArray2Str(characteristic.getValue()));
            if (mOnDataListener != null) {
                mOnDataListener.onCharacteristicChanged(characteristic.getUuid(), characteristic.getValue());
            }
        }
    };

    BLEDeviceOperator(Context context, String mac, BluetoothAdapter bluetoothAdapter) {
        Log.d(LOG_TAG, "Init " + mac + "'s operator");
        mMac = mac;
        mBluetoothAdapter = bluetoothAdapter;
        mHandler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_CONN_TIMEOUT:
                        Log.d(LOG_TAG, mMac + " conn timeout");
                        if (mOnConnResultListener != null) {
                            mOnConnResultListener.onResult(false, "timeout");
                            mOnConnResultListener = null;
                        }
                        disconnect();
                        break;
                    case MSG_WRITE_TIMEOUT:
                        Log.d(LOG_TAG, mMac + " write timeout");
                        if (mOnWriteResultListener != null) {
                            mOnWriteResultListener.onResult(false, "timeout");
                            mOnWriteResultListener = null;
                        }
                        break;
                    case MSG_READ_TIMEOUT:
                        Log.d(LOG_TAG, mMac + " read timeout");
                        if (mOnReadResultListener != null) {
                            mOnReadResultListener.onResult(false, "timeout");
                            mOnReadResultListener = null;
                        }
                        break;
                }
            }
        };
    }

    /**
     * 在主线程连接设备
     *
     * @param context context
     * @param listener 本次操作结果回调
     * @param timeoutMillis 超时时间，单位毫秒
     */
    void connect(final Context context, final OnResultListener listener, final long timeoutMillis) {
        if (mIsConnect) {
            if (mOnConnResultListener != null) {
                mOnConnResultListener.onResult(true, "has connected");
                mOnConnResultListener = null;
            }
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "connect device " + mMac);
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mMac);
                mOnConnResultListener = listener;
                mHandler.sendEmptyMessageDelayed(MSG_CONN_TIMEOUT, timeoutMillis);
                mGatt = device.connectGatt(context, false, mGattCallback);
            }
        });
    }

    /**
     * 在主线程断开连接
     *
     */
    void disconnect() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "disconnect device " + mMac);
                if (mGatt != null) {
                    mGatt.disconnect();
                }
            }
        });
    }

    /**
     * 关闭连接
     *
     */
    private void close() {
        close(true);
    }

    /**
     * 在主线程关闭连接
     *
     * @param enableCallback 是否调用回调
     */
    private void close(final boolean enableCallback) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "close device " + mMac);
                if (mGatt != null) {
                    mGatt.close();
                }
                mGatt = null;
                if (enableCallback) {
                    mOnStateListener.onClose();
                }
            }
        });
    }

    /**
     * 读取数据
     *
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param listener 本次操作结果回调
     * @param timeoutMillis 超时时间，单位毫秒
     */
    void read(UUID serviceUUID, UUID characteristicUUID, OnResultListener listener, long timeoutMillis) {
        if (!mIsConnect) {
            if (listener != null) {
                listener.onResult(false, "not connected");
            }
            return;
        }
        BluetoothGattCharacteristic characteristic = getCharacteristicByUUID(serviceUUID, characteristicUUID);
        mOnReadResultListener = listener;
        mHandler.sendEmptyMessageDelayed(MSG_READ_TIMEOUT, timeoutMillis);
        if (mGatt != null && characteristic != null) {
            Log.d(LOG_TAG, mMac + "read with timeout " + timeoutMillis);
            mGatt.readCharacteristic(characteristic);
        }
    }

    /**
     * 写入数据
     *
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param data 数据
     * @param listener 本次操作结果回调
     * @param timeoutMillis 超时时间，单位毫秒
     */
    void write(UUID serviceUUID, UUID characteristicUUID, byte[] data, OnResultListener listener, long timeoutMillis) {
        if (!mIsConnect) {
            if (listener != null) {
                listener.onResult(false, "not connected");
            }
            return;
        }
        BluetoothGattCharacteristic characteristic = getCharacteristicByUUID(serviceUUID, characteristicUUID);
        mOnWriteResultListener = listener;
        mHandler.sendEmptyMessageDelayed(MSG_WRITE_TIMEOUT, timeoutMillis);
        if (mGatt != null && characteristic != null) {
            Log.d(LOG_TAG, mMac + "read with timeout " + timeoutMillis + ", data = " + LogUtils.byteArray2Str(data));
            characteristic.setValue(data);
            mGatt.writeCharacteristic(characteristic);
        }
    }

    /**
     * 订阅设备的对应Descriptor
     *
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param descriptorUUID 描述UUID
     */
    void subscribe(UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID) {
        if (!mIsConnect) {
            return;
        }
        BluetoothGattCharacteristic characteristic = getCharacteristicByUUID(serviceUUID, characteristicUUID);
        if (mGatt == null || characteristic == null) {
            return;
        }
        mGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mGatt.writeDescriptor(descriptor);
            Log.d(LOG_TAG, mMac + "subscribe device");
        }
    }

    /**
     * 取消订阅设备的对应Descriptor
     *
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param descriptorUUID 描述UUID
     */
    void unSubscribe(UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID) {
        if (!mIsConnect) {
            return;
        }
        BluetoothGattCharacteristic characteristic = getCharacteristicByUUID(serviceUUID, characteristicUUID);
        if (mGatt == null || characteristic == null) {
            return;
        }
        mGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mGatt.writeDescriptor(descriptor);
            Log.d(LOG_TAG, mMac + "unsubscribe device");
        }
    }

    /**
     * 通过服务UUID和特征UUID获取Characteristic
     *
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @return Characteristic
     */
    private BluetoothGattCharacteristic getCharacteristicByUUID(UUID serviceUUID, UUID characteristicUUID) {
        if (mGatt == null) {
            return null;
        }
        BluetoothGattService service = mGatt.getService(serviceUUID);
        return service.getCharacteristic(characteristicUUID);
    }

    /**
     * 停止工作
     *
     */
    void stop() {
        Log.d(LOG_TAG, mMac + "'s operator stop working");
        mHandler.removeMessages(MSG_CONN_TIMEOUT);
        mHandler.removeMessages(MSG_WRITE_TIMEOUT);
        mHandler.removeMessages(MSG_READ_TIMEOUT);
        release();
    }

    /**
     * 释放资源
     *
     */
    private void release() {
        Log.d(LOG_TAG, mMac + "'s operator release resources");
        close(false);
        mMac = null;
        mBluetoothAdapter = null;
        mHandler = null;
    }

    /* -------------- Listener and setter -------------- */
    private OnStateListener mOnStateListener;

    void setOnStateListener(OnStateListener onStateListener) {
        mOnStateListener = onStateListener;
    }

    interface OnStateListener {
        void onConnectComplete(boolean success);

        void onServiceDiscover();

        void onDisconnect();

        void onClose();
    }

    private OnDataListener mOnDataListener;

    void setOnDataListener(OnDataListener onDataListener) {
        mOnDataListener = onDataListener;
    }

    interface OnDataListener {
        void onWrite(boolean success);
        void onRead(boolean success, byte[] data);
        void onCharacteristicChanged(UUID characteristicUUID, byte[] data);
    }

    private OnResultListener mOnConnResultListener;
    private OnResultListener mOnWriteResultListener;
    private OnResultListener mOnReadResultListener;

    interface OnResultListener {
        void onResult(boolean success, String msg);
    }
}
