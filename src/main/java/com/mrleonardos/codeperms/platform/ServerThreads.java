package com.mrleonardos.codeperms.platform;

import com.mrleonardos.codecore.api.util.Scheduler;

final class ServerThreads implements Scheduler {

    private final Scheduler core;
    private volatile Thread server;

    ServerThreads(Scheduler core) {
        this.core = core;
    }

    void attach(Thread serverThread) {
        server = serverThread;
    }

    @Override
    public void onMainThread(Runnable task) {
        if (Thread.currentThread() == server) {
            task.run();
            return;
        }
        core.onMainThread(task);
    }

    @Override
    public void afterTicks(int ticks, Runnable task) {
        core.afterTicks(ticks, task);
    }
}
