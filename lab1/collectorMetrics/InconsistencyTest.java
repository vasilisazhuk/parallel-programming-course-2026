import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class InconsistencyTest {
    private static final int WRITERS        = 4;
    private static final int SNAPSHOTS      = 10_000;
    private static final long TEST_DURATION_MS = 1_000;

    @Test
    public void inconsistencyTest()throws Exception{
        MetricsCollector collector = new LockStripedCollector();

        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        CountDownLatch start = new CountDownLatch(1);

        long[] bucketPerWriter = new long[WRITERS];

        for (int i = 0; i < WRITERS; i++) {
            final int idx = i;
            pool.submit(() -> {
                long local = 0;
                try {
                    start.await();
                    long endAt = System.currentTimeMillis() + TEST_DURATION_MS;
                    while (!stop.get() && System.currentTimeMillis() < endAt) {
                        collector.record(ThreadLocalRandom.current().nextLong(0, 1024));
                        local++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    bucketPerWriter[idx] = local;
                }
            });
        }
        start.countDown();

        int brokenLess = 0;   // sum(buckets) < count
        int brokenMore = 0;   // sum(buckets) > count
        int brokenTotal = 0;

        for (int i = 0; i < SNAPSHOTS; i++) {
            Snapshot snap = collector.snapshot();
            long bucketSum = 0;
            for (long b : snap.buckets()) bucketSum += b;

            if (bucketSum != snap.count()) {
                brokenTotal++;
                if (bucketSum < snap.count()) brokenLess++;
                else                          brokenMore++;
            }
        }

        stop.set(true);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "writers didn't finish");

        long expected = 0;
        for (long c : bucketPerWriter) expected += c;
        long finalCount = collector.snapshot().count();

        double brokenPct = 100.0 * brokenTotal / SNAPSHOTS;
        System.out.printf("Всего снимков: %d%n", SNAPSHOTS);
        System.out.printf("Битых: %d (%.2f%%)%n", brokenTotal, brokenPct);
        System.out.printf("  sum < count: %d%n", brokenLess);
        System.out.printf("  sum > count: %d%n", brokenMore);
        System.out.printf("Ожидаемый count: %d, финальный count: %d%n",
                expected, finalCount);
        assertEquals(expected, finalCount,
                "После остановки писателей count обязан совпасть");
        Snapshot last = collector.snapshot();
        long lastSum = 0;
        for (long b : last.buckets()) lastSum += b;
        assertEquals(last.count(), lastSum,
                "Финальный snapshot должен быть согласован");
    }
}
