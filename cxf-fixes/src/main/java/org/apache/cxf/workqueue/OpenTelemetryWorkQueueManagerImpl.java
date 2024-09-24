package org.apache.cxf.workqueue;

import jakarta.annotation.Resource;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.managers.WorkQueueImplMBeanWrapper;
import org.apache.cxf.bus.managers.WorkQueueManagerImpl;
import org.apache.cxf.buslifecycle.BusLifeCycleListener;
import org.apache.cxf.buslifecycle.BusLifeCycleManager;
import org.apache.cxf.common.injection.NoJSR250Annotations;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.ConfiguredBeanLocator;
import org.apache.cxf.management.InstrumentationManager;

import javax.management.JMException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@NoJSR250Annotations(unlessNull = "bus")
public class OpenTelemetryWorkQueueManagerImpl implements WorkQueueManager {

    public static final String DEFAULT_QUEUE_NAME = "default";
    public static final String DEFAULT_WORKQUEUE_BEAN_NAME = "cxf.default.workqueue";

    private static final Logger LOG =
            LogUtils.getL7dLogger(WorkQueueManagerImpl.class);

    Map<String, AutomaticWorkQueue> namedQueues
            = new ConcurrentHashMap<>(4, 0.75f, 2);

    boolean inShutdown;
    InstrumentationManager imanager;
    Bus bus;

    public OpenTelemetryWorkQueueManagerImpl() {
    }

    public OpenTelemetryWorkQueueManagerImpl(Bus b) {
        setBus(b);
    }

    public Bus getBus() {
        return bus;
    }

    @Resource
    public final void setBus(Bus bus) {
        this.bus = bus;
        if (null != bus) {
            bus.setExtension(this, WorkQueueManager.class);
            imanager = bus.getExtension(InstrumentationManager.class);

//            // TODO: https://github.com/apache/cxf/pull/2063
//            if (null != imanager) {
//                try {
//                    imanager.register(new WorkQueueManagerImplMBeanWrapper(this));
//                } catch (JMException jmex) {
//                    LOG.log(Level.WARNING, jmex.getMessage(), jmex);
//                }
//            }

            ConfiguredBeanLocator locator = bus.getExtension(ConfiguredBeanLocator.class);
            Collection<? extends AutomaticWorkQueue> q = locator
                    .getBeansOfType(AutomaticWorkQueue.class);
            if (q != null) {
                for (AutomaticWorkQueue awq : q) {
                    addNamedWorkQueue(awq.getName(), awq);
                }
            }

            if (!namedQueues.containsKey(DEFAULT_QUEUE_NAME)) {
                AutomaticWorkQueue defaultQueue
                        = locator.getBeanOfType(DEFAULT_WORKQUEUE_BEAN_NAME, AutomaticWorkQueue.class);
                if (defaultQueue != null) {
                    addNamedWorkQueue(DEFAULT_QUEUE_NAME, defaultQueue);
                }
            }

            bus.getExtension(BusLifeCycleManager.class)
                    .registerLifeCycleListener(new WQLifecycleListener());
        }
    }

    public synchronized AutomaticWorkQueue getAutomaticWorkQueue() {
        AutomaticWorkQueue defaultQueue = getNamedWorkQueue(DEFAULT_QUEUE_NAME);
        if (defaultQueue == null) {
            defaultQueue = createAutomaticWorkQueue();
        }
        return defaultQueue;
    }

    public synchronized void shutdown(boolean processRemainingTasks) {
        inShutdown = true;
        for (AutomaticWorkQueue q : namedQueues.values()) {
            if (q instanceof AutomaticWorkQueueImpl) {
                AutomaticWorkQueueImpl impl = (AutomaticWorkQueueImpl)q;
                if (impl.isShared()) {
                    synchronized (impl) {
                        impl.removeSharedUser();

                        if (impl.getShareCount() == 0
                            && imanager != null
                            && imanager.getMBeanServer() != null) {
                            try {
                                imanager.unregister(new WorkQueueImplMBeanWrapper(impl, this));
                            } catch (JMException jmex) {
                                LOG.log(Level.WARNING, jmex.getMessage(), jmex);
                            }
                        }
                    }
                } else {
                    q.shutdown(processRemainingTasks);
                }
            } else {
                q.shutdown(processRemainingTasks);
            }
        }

        synchronized (this) {
            notifyAll();
        }
    }

    public void run() {
        synchronized (this) {
            while (!inShutdown) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    // ignore
                }
            }
            for (AutomaticWorkQueue q : namedQueues.values()) {
                while (!q.isShutdown()) {
                    try {
                        wait(100L);
                    } catch (InterruptedException ex) {
                        // ignore
                    }
                }
            }
        }
        for (java.util.logging.Handler h : LOG.getHandlers())  {
            h.flush();
        }

    }

    public AutomaticWorkQueue getNamedWorkQueue(String name) {
        return namedQueues.get(name);
    }
    public final void addNamedWorkQueue(String name, AutomaticWorkQueue q) {
        namedQueues.put(name, q);
        if (q instanceof AutomaticWorkQueueImpl impl) {
            if (impl.isShared()) {
                synchronized (impl) {
                    if (impl.getShareCount() == 0
                        && imanager != null
                        && imanager.getMBeanServer() != null) {
                        try {
                            imanager.register(new WorkQueueImplMBeanWrapper(impl, this));
                        } catch (JMException jmex) {
                            LOG.log(Level.WARNING, jmex.getMessage(), jmex);
                        }
                    }
                    impl.addSharedUser();
                }
            } else if (imanager != null) {
                try {
                    imanager.register(new WorkQueueImplMBeanWrapper(impl, this));
                } catch (JMException jmex) {
                    LOG.log(Level.WARNING, jmex.getMessage(), jmex);
                }
            }
        }
    }

    // TODO: https://github.com/apache/cxf/pull/2063
    private AutomaticWorkQueue createAutomaticWorkQueue() {
        AutomaticWorkQueue q = new OpenTelemetryInstrumentedAutomaticWorkQueueImpl(DEFAULT_QUEUE_NAME);
        addNamedWorkQueue(DEFAULT_QUEUE_NAME, q);
        return q;
    }

    class WQLifecycleListener implements BusLifeCycleListener {

        @Override
        public void initComplete() {
        }

        @Override
        public void preShutdown() {
            shutdown(true);
        }

        @Override
        public void postShutdown() {
        }
    }
}
