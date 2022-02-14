package org.eclipse.dataspaceconnector.common.statemachine;

import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.retry.WaitStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class StateMachineLoop {

    private final List<Processor> processors = new ArrayList<>();
    private final ExecutorService executor;
    private final AtomicBoolean active = new AtomicBoolean();
    private final WaitStrategy waitStrategy;
    private final Monitor monitor;
    private final String name;
    private int shutdownTimeout = 10;

    private StateMachineLoop(String name, Monitor monitor, WaitStrategy waitStrategy) {
        this.name = name;
        this.monitor = monitor;
        this.waitStrategy = waitStrategy;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName("StateMachineLoop-" + name);
            return thread;
        });
    }

    public Future<?> start() {
        active.set(true);
        return executor.submit(loop());
    }

    public CompletableFuture<Boolean> stop() {
        active.set(false);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return executor.awaitTermination(shutdownTimeout, SECONDS);
            } catch (InterruptedException e) {
                monitor.severe(format("StateMachineLoop [%s] await termination failed", name), e);
                return false;
            }
        });
    }

    public boolean isActive() {
        return active.get();
    }

    private Runnable loop() {
        return () -> {
            while (active.get()) {
                try {
                    var processed = processors.stream()
                            .mapToLong(Processor::run)
                            .sum();

                    if (processed == 0) {
                        Thread.sleep(waitStrategy.waitForMillis());
                    }
                    waitStrategy.success();
                } catch (Error | InterruptedException e) {
                    active.set(false);
                    monitor.severe(format("StateMachineLoop [%s] unrecoverable error", name), e);
                } catch (Throwable e) {
                    try {
                        monitor.severe(format("StateMachineLoop [%s] error caught", name), e);
                        Thread.sleep(waitStrategy.retryInMillis());
                    } catch (InterruptedException ex) {
                        active.set(false);
                        monitor.severe(format("StateMachineLoop [%s] unrecoverable error", name), e);
                    }
                }
            }
        };
    }

    public static class Builder {

        private final StateMachineLoop loop;

        private Builder(String name, Monitor monitor, WaitStrategy waitStrategy) {
            this.loop = new StateMachineLoop(name, monitor, waitStrategy);
        }

        public static Builder newInstance(String name, Monitor monitor, WaitStrategy waitStrategy) {
            return new Builder(name, monitor, waitStrategy);
        }

        public Builder processor(Processor processor) {
            loop.processors.add(processor);
            return this;
        }

        public Builder shutdownTimeout(int seconds) {
            loop.shutdownTimeout = seconds;
            return this;
        }

        public StateMachineLoop build() {
            return loop;
        }
    }
}
