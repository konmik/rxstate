package util.rx2;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.Scheduler;
import io.reactivex.observers.SerializedObserver;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import util.racer.Racer;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static util.racer.Racer.race;

public class RxStateTest {

    private static final int MAX_ITERATIONS = Integer.MAX_VALUE;
    private static final int MAX_DURATION = 60000;
    private static final int THREADS_NUMBER = 20;

    long preventOptimization;

    @Test
    public void raceTest() throws Exception {
        AtomicLong delayMultiplier = new AtomicLong();

        AtomicLong done = new AtomicLong();

        Racer.run(MAX_ITERATIONS, MAX_DURATION, iterationNumber -> {

            // these are magic numbers, change them depending on amount of iterations
            // amount of iterations with these numbers must be about 10% smaller than with zero multiplier
            delayMultiplier.set(1000 + iterationNumber % 10000);

            List<Scheduler> schedulers = asList(
                    Schedulers.trampoline(), Schedulers.io(),
                    Schedulers.computation(), Schedulers.newThread(),
                    Schedulers.from(Executors.newSingleThreadExecutor()));
            Scheduler scheduler = schedulers.get(iterationNumber % schedulers.size());

            RxState<Integer> value = new RxState<>(0, scheduler);

            TestObserver<Integer> subscriber = new TestObserver<>();
            value.values(true).subscribe(new SerializedObserver<>(subscriber));

            List<List<Runnable>> threads = new ArrayList<>();

            for (int i = 0; i < THREADS_NUMBER; i++) {
                threads.add(asList(
                        () -> value.apply(it -> it + 1),
                        () -> value.apply(it -> it + 1),
                        () -> value.apply(it -> it + 1)));
            }

            race(threads);

            while (value.isEmitting()) {

            }

            List<Integer> values = subscriber.values();

            int expectedSize = 1 + 3 * THREADS_NUMBER;
            int size = values.size();
            assertEquals(format("values: %s", values), expectedSize, size);
            for (int i = 0; i < values.size(); i++) {
                assertEquals(i, (int) values.get(i));
            }

            done.incrementAndGet();
        });

        System.out.println(format(Locale.US,
                "iterations: %d, ignore: %d",
                done.get(), preventOptimization));
    }
}