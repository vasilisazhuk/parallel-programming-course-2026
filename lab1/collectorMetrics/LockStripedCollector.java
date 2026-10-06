import java.util.concurrent.atomic.AtomicLong;

public class LockStripedCollector implements MetricsCollector{

    private final long[] buckets = new long[256];
    private final Object[] locks = new Object[16];
    private AtomicLong count = new AtomicLong();
    private AtomicLong sum = new AtomicLong();
    private AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private AtomicLong max = new AtomicLong(Long.MIN_VALUE);

    public LockStripedCollector() {
        for (int i = 0; i < 16; i++) {
            locks[i] = new Object();
        };
    }
    private void updateMin(long v) {
        long cur;
        while (v < (cur = min.get())) {
            if (min.compareAndSet(cur, v)) return;
        }
    }

    private void updateMax(long v) {
        long cur;
        while (v > (cur = max.get())) {
            if (max.compareAndSet(cur, v)) return;
        }
    }
    private static long findPercentile(long[] buckets, long count, double q){
        long threshold = (long) (count * q);
        long acc = 0;
        for (int i = 0; i < 256; i ++){
            acc += buckets[i];
            if (acc >= threshold) {
                return i * 4L;
            }
        }
        return 255 * 4L;
    }

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value/4, 255);
        synchronized (locks[bucket&15]) {
            buckets[bucket]++;
        }
        count.incrementAndGet();
        sum.addAndGet(value);
        updateMax(value);
        updateMin(value);
    }

    @Override
    public Snapshot snapshot() {
        long[] copy = new long[256];
        for (int s = 0; s < 16; s++) {
            synchronized (locks[s]) {
                for (int b = s; b < 256; b += 16) {
                    copy[b] = buckets[b];
                }
            }
        }
        long c = count.get();
        long s = sum.get();
        long mn = min.get();
        long mx = max.get();

        long p50 = findPercentile(copy, c, 0.50);
        long p99 = findPercentile(copy, c, 0.99);
        return new Snapshot(copy, c, s, mn, mx, p50, p99);
    }
}
