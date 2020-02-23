package net.ckozak;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

import org.jboss.threads.EnhancedQueueExecutor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@org.openjdk.jmh.annotations.State(Scope.Benchmark)
@Measurement(iterations = 4, time = 7)
@Warmup(iterations = 2, time = 7)
// Use a consistent heap and GC configuration to avoid confusion between JVMS with differing GC defaults.
@Fork(
        value = 1,
        jvmArgs = {"-Xmx2g", "-Xms2g", "-XX:+UseParallelOldGC"})
public class ExecutorBenchmarks {

    @Param({"THREAD_POOL_EXECUTOR", "EQE_NO_LOCKS", "EQE_REENTRANT_LOCK", "EQE_SPIN_LOCK", "EQE_SYNCHRONIZED"})
    public ExecutorType factory;

    @Param ({ "1", "2", "4", "8", "14", "28" })
    public int cores;

    @Param ({ "16,16", "100,200" })
    public String executorThreads;

    public enum ExecutorType {
        THREAD_POOL_EXECUTOR(LockMode.NO_LOCKS /* any value */),
        EQE_NO_LOCKS(LockMode.NO_LOCKS),
        EQE_SYNCHRONIZED(LockMode.SYNCHRONIZED),
        EQE_SPIN_LOCK(LockMode.SPIN_LOCK),
        EQE_REENTRANT_LOCK(LockMode.REENTRANT_LOCK);

        private final LockMode mode;

        ExecutorType(LockMode mode) {
            this.mode = mode;
        }

        ExecutorService create(String executorThreads) {
            mode.install();
            String[] coreAndMax = executorThreads.split(",");
            if (coreAndMax.length != 2) {
                throw new IllegalStateException("Failed to parse " + executorThreads);
            }
            int coreThreads = Integer.parseInt(coreAndMax[0].trim());
            int maxThreads = Integer.parseInt(coreAndMax[1].trim());
            if (this == THREAD_POOL_EXECUTOR) {
                return new ThreadPoolExecutor(coreThreads, maxThreads,
                        1L, TimeUnit.MINUTES,
                        new LinkedBlockingQueue<>(),
                        Executors.defaultThreadFactory());
            }
            return new EnhancedQueueExecutor.Builder()
                    .setCorePoolSize(coreThreads)
                    .setMaximumPoolSize(maxThreads)
                    .setKeepAliveTime(Duration.ofMinutes(1))
                    .setThreadFactory(Executors.defaultThreadFactory())
                    .build();
        }
    }

    public enum LockMode {
        NO_LOCKS,
        SPIN_LOCK,
        SYNCHRONIZED,
        REENTRANT_LOCK;

        void install() {
            switch (this) {
                case NO_LOCKS:
                    System.setProperty("jboss.threads.eqe.tail-lock", "false");
                    System.setProperty("jboss.threads.eqe.head-lock", "false");
                    break;
                case SPIN_LOCK:
                    // default
                    break;
                case SYNCHRONIZED:
                    System.setProperty("jboss.threads.eqe.tail-synchronized", "true");
                    System.setProperty("jboss.threads.eqe.head-synchronized", "true");
                case REENTRANT_LOCK:
                    System.setProperty("jboss.threads.eqe.tail-spin", "false");
                    System.setProperty("jboss.threads.eqe.head-spin", "false");
                    break;
                default:
                    throw new IllegalStateException("Unknown state: " + this);
            }
        }
    }
    private ExecutorService executor;

    @Setup
    public void setup() {
        CoreAffinity.useCores(cores);
        executor = factory.create(executorThreads);
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        executor.shutdownNow();
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            throw new IllegalStateException("executor failed to teminate within 1 second");
        }
    }

    @Threads(32)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkSubmit(Blackhole blackhole) throws Exception {
        executor.submit(() -> blackhole.consume(Thread.currentThread().getId())).get();
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(ExecutorBenchmarks.class.getSimpleName())
                .build())
                .run();
    }
}
