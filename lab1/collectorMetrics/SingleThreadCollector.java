public class SingleThreadCollector implements MetricsCollector {

    private final long[] buckets = new long[256];
    private long count = 0;
    private long sum = 0;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

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
        buckets[bucket]++;
        count++;
        sum += value;
        min = value < min ? value: min;//Math.min(value, min);
        max = value > max ? value: max; //Math.max(value, max);
    }

    @Override
    public Snapshot snapshot() {
        long [] bucketsCopy = buckets.clone();
        long p50 = findPercentile(bucketsCopy, count, 50);
        long p99 = findPercentile(bucketsCopy, count, 99);
        return new Snapshot(
                bucketsCopy, count, sum,
                min, max, p50, p99
        );
    }
}
