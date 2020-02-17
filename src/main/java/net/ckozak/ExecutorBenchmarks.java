package net.ckozak;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
@Measurement(iterations = 3, time = 3)
@Warmup(iterations = 5, time = 3)
// Use a consistent heap and GC configuration to avoid confusion between JVMS with differing GC defaults.
@Fork(
        value = 1,
        jvmArgs = {"-Xmx2g", "-Xms2g", "-XX:+UseParallelOldGC"})
public class ExecutorBenchmarks {

    @Param({"THREAD_POOL_EXECUTOR", "EQE_NO_LOCKS", "EQE_SPIN_LOCK", "EQE_REENTRANT_LOCK"})
    public ExecutorType factory;

    @Param ({ "1", "2", "4", "8", "14", "28" })
    public int cores;

    public enum ExecutorType {
        THREAD_POOL_EXECUTOR(LockMode.NO_LOCKS /* any value */),
        EQE_NO_LOCKS(LockMode.NO_LOCKS),
        EQE_SPIN_LOCK(LockMode.SPIN_LOCK),
        EQE_REENTRANT_LOCK(LockMode.REENTRANT_LOCK);

        private final LockMode mode;

        ExecutorType(LockMode mode) {
            this.mode = mode;
        }

        ExecutorService create() {
            mode.install();
            if (this == THREAD_POOL_EXECUTOR) {
                return new ThreadPoolExecutor(100, 200,
                        1L, TimeUnit.MINUTES,
                        new LinkedBlockingQueue<>(),
                        Executors.defaultThreadFactory());
            }
            return new EnhancedQueueExecutor.Builder()
                    .setCorePoolSize(100)
                    .setMaximumPoolSize(200)
                    .setKeepAliveTime(Duration.ofMinutes(1))
                    .setThreadFactory(Executors.defaultThreadFactory())
                    .build();
        }
    }

    public enum LockMode {
        NO_LOCKS,
        SPIN_LOCK,
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
        executor = factory.create();
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
