package ai.ldzero.blewrapperdev.ble.taskqueue;

import android.util.Log;

/**
 * 把异步任务封装为同步任务的类
 * run()执行完后会自动阻塞，直到被调用finishTask()才恢复
 * 一般用法在异步操作的回调中调用finishTask()，达到异步任务变同步任务的效果
 *
 * Created on 2017/7/21.
 *
 * @author ldzero
 */

public abstract class WrappedAsyncTask implements ITask {

    private final String LOG_TAG = this.getClass().getSimpleName();

    private final Object mLock = new Object();

    private boolean mPause = true;

    @Override
    public void run() {
        setPause();
        _run();
        onPause();
    }

    public abstract void _run();

    public void finishTask() {
        setResume();
    }

    private void onPause() {
        if (mPause) {
            synchronized (mLock) {
                if (mPause) {
                    try {
                        Log.d(LOG_TAG, "task onPause");
                        mLock.wait();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void setResume() {
        Log.d(LOG_TAG, "task resume");
        if (mPause) {
            synchronized (mLock) {
                if (mPause) {
                    mPause = false;
                    mLock.notifyAll();
                    Log.d(LOG_TAG, "task resume success");
                }
            }
        }
    }

    private void setPause() {
        Log.d(LOG_TAG, "set task pause");
        if (!mPause) {
            synchronized (mLock) {
                mPause = true;
                Log.d(LOG_TAG, "set task pause success");
            }
        }
    }

}
