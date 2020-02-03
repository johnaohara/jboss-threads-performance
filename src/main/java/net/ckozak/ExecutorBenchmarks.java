package net.ckozak;

import java.util.concurrent.Executors;
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

    @Param({"NO_LOCKS", "SPIN_LOCK", "REENTRANT_LOCK"})
    public LockMode lockMode;

    @Param ({ "1", "2", "4", "8", "14" })
    public int cores;

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
    private EnhancedQueueExecutor eqe;

    @Setup
    public void setup() {
        lockMode.install();
        CoreAffinity.useCores(cores);
        eqe = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(100)
                .setMaximumPoolSize(200)
                .setKeepAliveTime(1, TimeUnit.MINUTES)
                .setThreadFactory(Executors.defaultThreadFactory())
                .build();
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        eqe.shutdownNow();
        if (!eqe.awaitTermination(1, TimeUnit.SECONDS)) {
            throw new IllegalStateException("executor failed to teminate within 1 second");
        }
    }

    @Threads(32)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkSubmit(Blackhole blackhole) throws Exception {
        eqe.submit(() -> blackhole.consume(Thread.currentThread().getId())).get();
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(ExecutorBenchmarks.class.getSimpleName())
                .build())
                .run();
    }
}
