package util.racer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static java.lang.Math.max;
import static java.lang.Thread.sleep;
import static java.util.Collections.shuffle;

public class Racer {

    public static boolean completed(List<RacerThread> threads) {
        for (RacerThread thread : threads) {
            if (!thread.completed())
                return false;
        }
        return true;
    }

    public static void race(List<List<Runnable>> threads) {

        List<RacerThread> racerThreads = new ArrayList<>();

        int maxSteps = 0;
        for (List<Runnable> thread : threads) {
            maxSteps = max(maxSteps, thread.size());
            racerThreads.add(new RacerThread(thread));
        }

        for (int i = 0; i < maxSteps; i++) {
            shuffle(racerThreads);
            for (RacerThread thread : racerThreads) {
                thread.next();
            }
        }

        while (!completed(racerThreads)) {
            try {
                sleep(0);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void acquire(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void run(int retry, int timeLimit, Iteration iteration) {
        final long time1 = now();
        for (int i = 0; i < retry && now() - time1 < timeLimit; i++) {
            iteration.run(i);
        }
    }

    private static long now() {
        return System.nanoTime() / 1000000;
    }

    public interface Iteration {
        void run(int iterationNumber);
    }
}
