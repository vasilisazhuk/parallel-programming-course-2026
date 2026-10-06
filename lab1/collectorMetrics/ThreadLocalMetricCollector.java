import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalMetricCollector implements MetricsCollector{
    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int b = (int) Math.min(value / 4, 255);

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        // min и max обновляются обычным сравнением без CAS:
        if (value < s.min.getPlain()) s.min.setRelease(value);
        if (value > s.max.getPlain()) s.max.setRelease(value);
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
        List<ThreadState> states;
        synchronized (listLock) {
            states = new ArrayList<>(allStates);
        }
        long[] out = new long[256];
        long count = 0, sum = 0;
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        boolean any = false;

        for (ThreadState s : states) {
            long c = s.count.get();
            if (c == 0) continue;

            any = true;
            for (int i = 0; i < 256; i++) {
                out[i] += s.buckets.get(i);
            }
            count += c;
            sum   += s.sum.get();

            long sMin = s.min.get();
            long sMax = s.max.get();
            if (sMin < min) min = sMin;
            if (sMax > max) max = sMax;
        }

        if (!any) {
            // Ни одного record() ещё не было — отдаём пустой снапшот.
            return new Snapshot(out, 0, 0, 0, 0, 0, 0);
        }

        long p50 = findPercentile(out, count, 0.50);
        long p99 = findPercentile(out, count, 0.99);
        return new Snapshot(out, count, sum, min, max, p50, p99);
        //return null;
    }

    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }
    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });
}
