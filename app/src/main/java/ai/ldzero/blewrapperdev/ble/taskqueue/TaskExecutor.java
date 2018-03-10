package ai.ldzero.blewrapperdev.ble.taskqueue;

import android.util.Log;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * 任务执行器，维持着一个任务队列。
 * 开始工作后不断从队列取出任务并同步执行，若任务队列为空，阻塞线程。
 *
 * Created on 2017/7/21.
 *
 * @author ldzero
 */

public class TaskExecutor extends Thread {

    private final String LOG_TAG = this.getClass().getSimpleName();

    public TaskExecutor(int taskCount) {
        mTaskQueue = new ArrayBlockingQueue<>(taskCount);
    }

    /* 是否处于运行状态 */
    private boolean mIsRunning = false;

    /* 是否处于挂起状态 */
    private boolean mPause = true;

    /* 锁 */
    private final Object mLock = new Object();

    /* 任务队列 */
    private Queue<ITask> mTaskQueue;

    // TODO: 增加根据任务标志移除某些任务的方法

    /**
     * 添加任务到队列
     *
     * @param task 任务
     * @return 是否添加成功
     */
    public boolean addTask(ITask task) {
        Log.d(LOG_TAG, "add task");
        boolean result = mTaskQueue.offer(task);
        resumeWorking();
        return result;
    }

    @Override
    public void run() {
        Log.d(LOG_TAG, "executor start running");
        while (mIsRunning) {
            onPause();
            if (!mIsRunning) {
                break;
            }
            try {
                // 取出任务，执行，若没有任务，阻塞线程
                Log.d(LOG_TAG, "poll task");
                ITask task = mTaskQueue.poll();
                if (task == null) {
                    setPauseWorking();
                } else {
                    Log.d(LOG_TAG, "execute task");
                    task.run();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (!mIsRunning) {
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "executor stop running");
    }

    /**
     * 开始工作
     *
     */
    public void startWorking() {
        mIsRunning = true;
        start();
    }

    /**
     * 回复线程
     *
     */
    private void resumeWorking() {
        Log.d(LOG_TAG, "executor resume");
        if (mPause) {
            synchronized (mLock) {
                if (mPause) {
                    mPause = false;
                    mLock.notifyAll();
                    Log.d(LOG_TAG, "executor resume success");
                }
            }
        }
    }

    /**
     * 判断是否处于挂起状态，处于暂停状态则做进行挂起
     *
     */
    private void onPause() {
        if (mPause) {
            synchronized (mLock) {
                if (mPause) {
                    try {
                        Log.d(LOG_TAG, "executor onPause");
                        mLock.wait();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 设置设备进入挂起状态，线程下一次进入onPause时将阻塞在锁上
     *
     */
    private void setPauseWorking() {
        Log.d(LOG_TAG, "set executor pause");
        if (!mPause) {
            synchronized (mLock) {
                if (!mPause) {
                    mPause = true;
                    Log.d(LOG_TAG, "set executor pause success");
                }
            }
        }
    }

    /**
     * 停止工作
     *
     */
    public void stopWorking() {
        Log.d(LOG_TAG, "executor stop working");
        mIsRunning = false;
        resumeWorking();
        release();
    }

    /**
     * 释放资源
     *
     */
    private void release() {
        Log.d(LOG_TAG, "executor release resources");
        mTaskQueue = null;
    }
}
