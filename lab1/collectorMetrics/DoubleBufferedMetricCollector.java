import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class DoubleBufferedMetricCollector implements MetricsCollector{

    private long globalCount = 0;
    private long globalSum = 0;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax = Long.MIN_VALUE;
    private final long[] globalBuckets = new long[256];
    static final class ThreadBuffers {
        // Два буфера: [0] и [1]. Обычные массивы и переменные!
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        // Флаг входа: -1 = вне буферов, 0 = запись в буфер 0, 1 = запись в буфер 1
        final AtomicInteger inside = new AtomicInteger(-1);
    }

    @Override
    public void record(long value) {
        ThreadBuffers s = myState.get();
        int b; //= (int) Math.min(value / 4, 255);

        while (true) {
            b = active;
            s.inside.set(b);
            //break;
            if (active == b) break;
            s.inside.setRelease(-1);
        }
        int bucket = (int) Math.min(value / 4, 255);

        s.buckets[b][bucket]++;
        s.count[b]++;
        s.sum[b] += value;
        if (value < s.min[b]) s.min[b] = value;
        if (value > s.max[b]) s.max[b] = value;

        s.inside.setRelease(-1);
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
    public Snapshot snapshot() {
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;

            for (ThreadBuffers s : allStates) {
                while (s.inside.get() == old) Thread.onSpinWait();

                for (int i = 0; i < 256; i++) globalBuckets[i] += s.buckets[old][i];
                globalCount += s.count[old];
                globalSum   += s.sum[old];
                if (s.min[old] < globalMin) globalMin = s.min[old];
                if (s.max[old] > globalMax) globalMax = s.max[old];

                // обнуляем old-буфер
                java.util.Arrays.fill(s.buckets[old], 0);
                s.count[old] = 0;
                s.sum[old]   = 0;
                s.min[old]   = Long.MAX_VALUE;
                s.max[old]   = 0;
            }

            if (globalCount == 0) return new Snapshot(new long[256], 0, 0, 0, 0, 0, 0);

            long p50 = findPercentile(globalBuckets, globalCount, 0.50);
            long p99 = findPercentile(globalBuckets, globalCount, 0.99);
            return new Snapshot(globalBuckets.clone(), globalCount, globalSum,
                    globalMin, globalMax, p50, p99);
        }
    }

    private volatile int active = 0;
    private final List<ThreadBuffers> allStates = new ArrayList<>();
    private final Object snapLock = new Object();

    private final ThreadLocal<ThreadBuffers> myState = ThreadLocal.withInitial(() -> {
        ThreadBuffers s = new ThreadBuffers();
        synchronized (snapLock) { allStates.add(s); }
        return s;
    });
}
