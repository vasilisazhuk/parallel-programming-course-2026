public class MultiThreadCollector implements MetricsCollector {

    private final SingleThreadCollector delegate = new SingleThreadCollector();

    @Override
    public void record(long value) {
        synchronized (delegate) {
            delegate.record(value);
        }
    }

    @Override
    public synchronized Snapshot snapshot() {
            return delegate.snapshot();
    }
}
