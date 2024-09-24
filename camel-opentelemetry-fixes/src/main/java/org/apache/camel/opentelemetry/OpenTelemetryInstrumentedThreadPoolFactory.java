package org.apache.camel.opentelemetry;

import io.opentelemetry.context.Context;
import org.apache.camel.opentelemetry.internal.CurrentContextScheduledExecutorService;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.DefaultThreadPoolFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@JdkService(ThreadPoolFactory.FACTORY)
public class OpenTelemetryInstrumentedThreadPoolFactory extends DefaultThreadPoolFactory {

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return Context.taskWrapping(super.newCachedThreadPool(threadFactory));
    }

    @Override
    public ExecutorService newThreadPool(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            int maxQueueSize,
            boolean allowCoreThreadTimeOut,
            RejectedExecutionHandler rejectedExecutionHandler,
            ThreadFactory threadFactory)
            throws IllegalArgumentException {

        ExecutorService executorService = super.newThreadPool(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                timeUnit,
                maxQueueSize,
                allowCoreThreadTimeOut,
                rejectedExecutionHandler,
                threadFactory);

        return Context.taskWrapping(executorService);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        return new CurrentContextScheduledExecutorService(super.newScheduledThreadPool(profile, threadFactory));
    }
}
