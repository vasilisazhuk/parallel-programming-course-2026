public class EmptyLockCollector implements MetricsCollector {

    private final Object lock = new Object();

    @Override
    public void record(long value) {
        synchronized (lock) {

        }
    }

    @Override
    public Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(new long[256], 0, 0, 0, 0, 0, 0);
        }
    }
}