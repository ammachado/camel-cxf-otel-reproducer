package org.apache.cxf.workqueue;

import io.opentelemetry.context.Context;

public class OpenTelemetryInstrumentedAutomaticWorkQueueImpl extends AutomaticWorkQueueImpl {

    public OpenTelemetryInstrumentedAutomaticWorkQueueImpl() {
        this(DEFAULT_MAX_QUEUE_SIZE);
    }

    public OpenTelemetryInstrumentedAutomaticWorkQueueImpl(String name) {
        this(DEFAULT_MAX_QUEUE_SIZE, name);
    }

    public OpenTelemetryInstrumentedAutomaticWorkQueueImpl(int max) {
        this(max, "default");
    }

    public OpenTelemetryInstrumentedAutomaticWorkQueueImpl(int max, String name) {
        this(max,
                0,
                25,
                5,
                2 * 60 * 1000L,
                name);
    }

    public OpenTelemetryInstrumentedAutomaticWorkQueueImpl(
            int mqs,
            int initialThreads,
            int highWaterMark,
            int lowWaterMark,
            long dequeueTimeout
    ) {
        this(mqs, initialThreads, highWaterMark, lowWaterMark, dequeueTimeout, "default");
    }

    public OpenTelemetryInstrumentedAutomaticWorkQueueImpl(
            int mqs,
            int initialThreads,
            int highWaterMark,
            int lowWaterMark,
            long dequeueTimeout,
            String name
    ) {
        super(mqs, initialThreads, highWaterMark, lowWaterMark, dequeueTimeout, name);
    }

    @Override
    public void execute(Runnable command) {
        super.execute(Context.current().wrap(command));
    }

    @Override
    public synchronized void schedule(Runnable work, long delay) {
        super.schedule(Context.current().wrap(work), delay);
    }
}
