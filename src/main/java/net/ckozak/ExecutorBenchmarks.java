package net.ckozak;

import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossExecutors;
import org.jboss.threads.JBossThreadFactory;
import org.jboss.threads.StripedEnhancedQueueExecutor;
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

import java.time.Duration;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@org.openjdk.jmh.annotations.State(Scope.Benchmark)
@Measurement(iterations = 4, time = 7)
@Warmup(iterations = 2, time = 7)
// Use a consistent heap and GC configuration to avoid confusion between JVMS with differing GC defaults.
@Fork(
        value = 1,
        jvmArgs = {"-Xmx2g", "-Xms2g", "-XX:+UseParallelOldGC"})
public class ExecutorBenchmarks {

//    @Param({"THREAD_POOL_EXECUTOR_LBQ", "THREAD_POOL_EXECUTOR_LTQ", "EQE_NO_LOCKS", "EQE_REENTRANT_LOCK", "EQE_SPIN_LOCK", "EQE_SYNCHRONIZED"})
    @Param({"THREAD_POOL_EXECUTOR_LBQ", "THREAD_POOL_EXECUTOR_LTQ", "EQE_NO_LOCKS", "EQE_REENTRANT_LOCK", "EQE_SPIN_LOCK"
            , "EQE_SYNCHRONIZED", "SEQE_NO_LOCKS", "SEQE_REENTRANT_LOCK", "SEQE_SPIN_LOCK", "SEQE_SYNCHRONIZED"})
    public ExecutorType factory;

    @Param ({ "1", "2", "4", "8", "14", "28" })
    public int cores;

    @Param ({ "16,16", "100,200" })
    public String executorThreads;


    public enum ExecutorType {
        THREAD_POOL_EXECUTOR_LBQ(LockMode.SPIN_LOCK /* no configuration */, tfeLbqFactory),
        THREAD_POOL_EXECUTOR_LTQ(LockMode.SPIN_LOCK /* no configuration */, tfeLtqFactory),
        EQE_NO_LOCKS(LockMode.NO_LOCKS, eqeFactory),
        EQE_SYNCHRONIZED(LockMode.SYNCHRONIZED, eqeFactory),
        EQE_SPIN_LOCK(LockMode.SPIN_LOCK, eqeFactory),
        EQE_REENTRANT_LOCK(LockMode.REENTRANT_LOCK, eqeFactory),
        SEQE_NO_LOCKS(LockMode.NO_LOCKS, seqeFactory),
        SEQE_SYNCHRONIZED(LockMode.SYNCHRONIZED, seqeFactory),
        SEQE_SPIN_LOCK(LockMode.SPIN_LOCK, seqeFactory),
        SEQE_REENTRANT_LOCK(LockMode.REENTRANT_LOCK, seqeFactory);
        ;

        private final LockMode mode;
        private final ExecutorFactory executorFactory;

        ExecutorType(LockMode mode, ExecutorFactory executorFactory) {
            this.mode = mode;
            this.executorFactory = executorFactory;
        }

        ExecutorService create(String executorThreads) {
            mode.install();
            String[] coreAndMax = executorThreads.split(",");
            if (coreAndMax.length != 2) {
                throw new IllegalStateException("Failed to parse " + executorThreads);
            }
            int coreThreads = Integer.parseInt(coreAndMax[0].trim());
            int maxThreads = Integer.parseInt(coreAndMax[1].trim());

            return executorFactory.build(coreThreads, maxThreads);

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

    interface ExecutorFactory {
        ExecutorService build(int coreThreads, int maxThreads);
    }

    static ExecutorFactory tfeLbqFactory = (coreThreads, maxThreads) -> new ThreadPoolExecutor(coreThreads, maxThreads,
            1L, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(),
            Executors.defaultThreadFactory());

    static ExecutorFactory tfeLtqFactory = (coreThreads, maxThreads) -> new ThreadPoolExecutor(coreThreads, maxThreads,
            1L, TimeUnit.MINUTES,
            new LinkedTransferQueue<>(),
            Executors.defaultThreadFactory());

    static ExecutorFactory eqeFactory = (coreThreads, maxThreads) -> new EnhancedQueueExecutor.Builder()
            .setRegisterMBean(false)
            .setHandoffExecutor(JBossExecutors.rejectingExecutor())
            .setThreadFactory(JBossExecutors.resettingThreadFactory(getThreadFactory()))
            .setCorePoolSize(coreThreads)
            .setMaximumPoolSize(maxThreads)
            .setKeepAliveTime(Duration.ofMinutes(1))
            .build();

    static ExecutorFactory seqeFactory = (coreThreads, maxThreads) -> new StripedEnhancedQueueExecutor.Builder()
            .setRegisterMBean(false)
            .setHandoffExecutor(JBossExecutors.rejectingExecutor())
            .setThreadFactory(JBossExecutors.resettingThreadFactory(getThreadFactory()))
            .setCorePoolSize(coreThreads)
            .setMaximumPoolSize(maxThreads)
            .setKeepAliveTime(Duration.ofMinutes(1))
            .build();

    private static final JBossThreadFactory getThreadFactory(){
        return new JBossThreadFactory(new ThreadGroup("executor"), Boolean.TRUE, null,
                "executor-thread-%t", JBossExecutors.loggingExceptionHandler("org.jboss.executor.uncaught"), null);
    }


}
