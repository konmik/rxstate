package util.racer;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This simple class allows to test for race conditions.
 * Use {@link Racer#race(List)}.
 */
public class RacerThread {

    private final Semaphore semaphore = new Semaphore(0);
    private final AtomicBoolean completed = new AtomicBoolean();

    public RacerThread(final Iterable<Runnable> list) {
        new Thread(() -> {
            for (Runnable runnable : list) {
                Racer.acquire(semaphore);
                runnable.run();
            }
            completed.set(true);
        }).start();
    }

    public void next() {
        semaphore.release();
    }

    public boolean completed() {
        return completed.get();
    }
}