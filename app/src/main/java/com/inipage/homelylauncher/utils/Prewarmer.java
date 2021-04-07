package com.inipage.homelylauncher.utils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * App wakeup helper. Prepare items that we expect to use eventually, but not immediately. Jobs
 * submitted may be killed without warning.
 */
public class Prewarmer {

    private static Prewarmer s_INSTANCE;

    private final ThreadPoolExecutor mThreadPoolExecutor;

    private Prewarmer() {
        final BlockingQueue<Runnable> decodeWorkQueue = new LinkedBlockingQueue<>();
        mThreadPoolExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            500,
            TimeUnit.MILLISECONDS,
            decodeWorkQueue);
    }

    public static Prewarmer getInstance() {
        if (s_INSTANCE == null) {
            s_INSTANCE = new Prewarmer();
        }
        return s_INSTANCE;
    }

    public void prewarm(Runnable runnable) {
        mThreadPoolExecutor.execute(runnable);
    }
}
