import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Benchmark {

    public static double runSingleThread(MetricsCollector collector, int[] history, int T, int seconds) throws InterruptedException {

        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);

        long[] ops = new long[T];

        Thread[] threads = new Thread[T];

        for (int i = 0; i < T; i ++){
            final int index = i;
            threads[index] = new Thread(new Runnable() {
                @Override
                public void run() {
                    long local = 0;
                    int p = index * 1000;
                    try {
                        start.await();
                    } catch ( InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                    //System.out.println("thread " + index + " started, stop=" + stop.get());
                    while (!stop.get()) {
                        collector.record(history[p]);
                        local++;
                        p++;
                        if (p == history.length) p = 0;
                    }
                    ops[index] = local;
                    //System.out.println("thread " + index + " finished, local=" + local + ", stop=" + stop.get());
                }
            });
            threads[i].start();
        }

        long t0 = System.nanoTime();
        start.countDown();
        Thread.sleep(seconds * 1000L);
        stop.set(true);
        long t1 = System.nanoTime();
        for (Thread t : threads) t.join();
        long opsSum = Arrays.stream(ops).sum();
        return (double) opsSum / (t1 - t0) * 1e9;
    }

    public static double measurePoint(MetricsCollector collector,
                                      int[] history, int T) throws Exception {
        runSingleThread(collector, history, T, 5);
        double[] results = new double[5];
        for (int r = 0; r < 5; r++) {
            results[r] = runSingleThread(collector, history, T, 5);
        }
        System.out.println(collector.snapshot().count());
        Arrays.sort(results);
        return results[2];
        //return results;
    }

    public static void main(String[] args) throws Exception {

        int [] history = ParetoGen.generate(1<< 20, 42L);

        /*
        MetricsCollector singleThreadCollector = new SingleThreadCollector();
        double opsPerSecBaseline = measurePoint(singleThreadCollector, history, 1);
        System.out.printf("T=1 baseline: %,.0f ops/sec%n", opsPerSecBaseline);
        */
        int cores = Runtime.getRuntime().availableProcessors();

        for (int i = 0; i <= cores * 2; i ++) {
            MetricsCollector syncThreadCollector = new MultiThreadCollector();
            double opsPerSecSync = measurePoint(syncThreadCollector, history, i);
            System.out.printf("T=2 (%d threads) baseline: %,.0f ops/sec%n", i, opsPerSecSync);
        }
        /*
        for (int i = 0; i <= cores * 2; i ++) {
            MetricsCollector lockStripedCollector = new LockStripedCollector();
            double opsPerSecSync = measurePoint(lockStripedCollector, history, i);
            System.out.printf("T=3 (%d threads) baseline: %,.0f ops/sec%n", i, opsPerSecSync);
        }
        */
    }
}