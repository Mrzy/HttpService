package com.novoda.httpservice.provider;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;

import android.content.Intent;

public class EventBusTestConcurrency {

    private IntentRegistry intentRegistry;

    private IntentWrapper intentWrapper;

    private Map<String, List<IntentWrapper>> registry = Collections
            .synchronizedMap(new HashMap<String, List<IntentWrapper>>());

    @Before
    public void setupIntentRegistry() {
        List<IntentWrapper> l = new ArrayList<IntentWrapper>();
        l.add(new IntentWrapper(mock(Intent.class)));
        l.add(new IntentWrapper(mock(Intent.class)));
        l.add(new IntentWrapper(mock(Intent.class)));
        l.add(new IntentWrapper(mock(Intent.class)));
        l.add(new IntentWrapper(mock(Intent.class)));
        l.add(new IntentWrapper(mock(Intent.class)));
        l.add(new IntentWrapper(mock(Intent.class)));
        registry.put("something", l);
        intentRegistry = mock(IntentRegistry.class);
        intentWrapper = mock(IntentWrapper.class);
        when(intentRegistry.getSimilarIntents(any(IntentWrapper.class))).thenReturn(registry.get("something"));
    }

    @Test
    public void testConcurrency() throws InterruptedException {
        bus = new EventBus(intentRegistry);
        bus.fireOnContentConsumed(null);

        runMultiThread();
        assertTrue(true);
    }

    private void runMultiThread() throws InterruptedException {
        int i = 0;
        while (i < 1000) {
            CountDownLatch latch = new CountDownLatch(5);
            new RunMultiTimes(latch).start();
            new RunMultiTimes(latch).start();
            new RunMultiTimes(latch).start();
            new RunMultiTimes(latch).start();
            new RunMultiTimes(latch).start();
            latch.await();
            ++i;
        }
    }

    private class RunMultiTimes extends Thread {
        private CountDownLatch latch;

        public RunMultiTimes(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void run() {
            bus.fireOnContentConsumed(intentWrapper);
            latch.countDown();
            super.run();
        }
    }

    private EventBus bus;
}
