package org.apache.camel.opentelemetry;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.spi.annotations.JdkService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

// needs extension point from camel-main to create instance of this class instead of the default
@JdkService("executor-service-manager")
public class OpenTelemetryInstrumentedExecutorServiceManager extends DefaultExecutorServiceManager {

    public OpenTelemetryInstrumentedExecutorServiceManager(CamelContext camelContext) {
        super(camelContext);
    }

    //
    // Here, the proposed change is to remove this method and return a single-thread executor service (which would be
    // constructed by the current thread pool factory infrastructure available on Camel):
    //
    // <pre>{@code
    //     return Executors.newSingleThreadExecutor();
    // }</pre>
    //
    // Virtual thread support for Java 21 would also benefit from this change.
    //
    @Override
    public Thread newThread(String name, Runnable runnable) {
        return super.newThread(name, runnable);
    }

    //
    // This method should return an executor service, so that OpenTelemetry can intercept Camel's internal thread
    // creation to provide context propagation. With these changes, the custom thread pool factory implementation can
    // be removed
    //
    // This functionality is not yet implemented in OpenTelemetry,
    // please see https://github.com/open-telemetry/opentelemetry-java/pull/6712.
    //
    @Override
    protected void onNewExecutorService(ExecutorService executorService) {
        // executorService = super.onNewExecutorService(executorService);

        if (executorService instanceof ScheduledExecutorService scheduledExecutorService) {
            //return new CurrentContextScheduledExecutorService(scheduledExecutorService);
        }

        //return Context.taskWrapping(executorService);
    }
}
