package me.stevenkin.boomvc.server.kit;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.LongAdder;

public class NameThreadFactory implements ThreadFactory {
    private final String prefix;
    private final LongAdder threadNumber = new LongAdder();

    public NameThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        threadNumber.add(1);
        return new Thread(runnable, prefix + "threadㄧ" + threadNumber.intValue());
    }
}
