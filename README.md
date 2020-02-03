jboss-threads benchmarking with various lock configurations

Note: the CoreAffinity.java class shells out to run `taskset -a -p <mask> <java pid>`. This
requires JRE9+ runtime, however the code can build on jdk8.

Initial results:
```
Benchmark                           (cores)      (lockMode)   Mode  Cnt        Score        Error  Units
ExecutorBenchmarks.benchmarkSubmit        1        NO_LOCKS  thrpt    3   175427.334 ±  99633.942  ops/s
ExecutorBenchmarks.benchmarkSubmit        1       SPIN_LOCK  thrpt    3   136126.723 ± 832505.826  ops/s
ExecutorBenchmarks.benchmarkSubmit        1  REENTRANT_LOCK  thrpt    3   166802.606 ± 220491.622  ops/s
ExecutorBenchmarks.benchmarkSubmit        2        NO_LOCKS  thrpt    3   238119.673 ±  57374.632  ops/s
ExecutorBenchmarks.benchmarkSubmit        2       SPIN_LOCK  thrpt    3   163173.290 ± 479121.888  ops/s
ExecutorBenchmarks.benchmarkSubmit        2  REENTRANT_LOCK  thrpt    3   202860.295 ± 224134.046  ops/s
ExecutorBenchmarks.benchmarkSubmit        4        NO_LOCKS  thrpt    3   389107.805 ± 277479.225  ops/s
ExecutorBenchmarks.benchmarkSubmit        4       SPIN_LOCK  thrpt    3    82818.469 ±  70899.526  ops/s
ExecutorBenchmarks.benchmarkSubmit        4  REENTRANT_LOCK  thrpt    3   220755.634 ±   9220.538  ops/s
ExecutorBenchmarks.benchmarkSubmit        8        NO_LOCKS  thrpt    3   727343.230 ±  73120.671  ops/s
ExecutorBenchmarks.benchmarkSubmit        8       SPIN_LOCK  thrpt    3   217107.857 ±  31126.494  ops/s
ExecutorBenchmarks.benchmarkSubmit        8  REENTRANT_LOCK  thrpt    3   211775.638 ±  46812.757  ops/s
ExecutorBenchmarks.benchmarkSubmit       14        NO_LOCKS  thrpt    3  1112300.950 ± 103541.988  ops/s
ExecutorBenchmarks.benchmarkSubmit       14       SPIN_LOCK  thrpt    3   204114.786 ±  38581.330  ops/s
ExecutorBenchmarks.benchmarkSubmit       14  REENTRANT_LOCK  thrpt    3   208072.127 ±  33223.526  ops/s
```
