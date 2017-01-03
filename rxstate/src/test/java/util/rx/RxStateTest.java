package util.rx;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import rx.Scheduler;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import util.racer.Racer;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedList;
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
        Random random = new Random();
        RxState.raceTestDelay = () -> {
            double target = random.nextDouble() * delayMultiplier.get();
            for (int i = 0; i < target; i++) {
                preventOptimization++;
            }
        };

        AtomicLong done = new AtomicLong();

        Racer.run(MAX_ITERATIONS, MAX_DURATION, iterationNumber -> {

            // these are magic numbers, change them depending on amount of iterations
            // amount of iterations with these numbers must be about 10% smaller than with zero multiplier
            delayMultiplier.set(1000 + iterationNumber % 10000);

            List<Scheduler> schedulers = asList(
                    Schedulers.immediate(), Schedulers.io(),
                    Schedulers.computation(), Schedulers.newThread(),
                    Schedulers.from(Executors.newSingleThreadExecutor()));
            Scheduler scheduler = schedulers.get(iterationNumber % schedulers.size());

            RxState<Integer> value = new RxState<>(0, scheduler);

            List<TestSubscriber<Integer>> subscribers = synchronizedList(new ArrayList<>());
            List<RxState.StartWith> startWiths = synchronizedList(new ArrayList<>());

            List<List<Runnable>> threads = new ArrayList<>();

            for (int i = 0; i < THREADS_NUMBER; i++) {
                int finalI = i;
                threads.add(asList(
                        () -> {
                            TestSubscriber<Integer> subscriber = new TestSubscriber<>();
                            subscribers.add(subscriber);
                            RxState.StartWith startWith = RxState.StartWith.values()[(iterationNumber + finalI) % (RxState.StartWith.values().length - 1) + 1];
                            startWith = startWith == RxState.StartWith.IMMEDIATE ? RxState.StartWith.SCHEDULE : startWith;
                            startWiths.add(startWith);
                            value.values(startWith).subscribe(subscriber);
                        },
                        () -> value.apply(it -> it + 1),
                        () -> value.apply(it -> it + 1),
                        () -> value.apply(it -> it + 1)));
            }
            // TODO: write a separate test for StartWith.IMMEDIATE

            race(threads);

            while (value.isEmitting()) {
            }

            for (int s = 0; s < subscribers.size(); s++) {
                TestSubscriber<Integer> subscriber = subscribers.get(s);
                List<Integer> values = subscriber.getOnNextEvents();
                if (values.size() > 0) {
                    ArrayList<Integer> expected = new ArrayList<>(values.size());
                    for (int i = 0, v = values.get(0); i < values.size(); i++, v++) {
                        expected.add(v);
                    }
                    assertEquals("startWith: " + startWiths.get(s), expected, values);
                }
            }

            done.incrementAndGet();
        });

        System.out.println(format(Locale.US,
                "iterations: %d, ignore: %d",
                done.get(), preventOptimization));
    }

    @Test
    public void startImmediate() {
        RxState<Integer> state = new RxState<>(0, Schedulers.immediate());
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        state.values(RxState.StartWith.IMMEDIATE).subscribe(subscriber);
        subscriber.assertValues(0);
        state.apply(it -> it + 1);
        subscriber.assertValues(0, 1);
    }

    @Test
    public void startNo() {
        RxState<Integer> state = new RxState<>(0, Schedulers.immediate());
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        state.values(RxState.StartWith.NO).subscribe(subscriber);
        subscriber.assertValues();
        state.apply(it -> it + 1);
        subscriber.assertValues(1);
    }

    @Test
    public void startSchedule() {
        TestScheduler scheduler = new TestScheduler();
        RxState<Integer> state = new RxState<>(0, scheduler);
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        state.values(RxState.StartWith.SCHEDULE).subscribe(subscriber);
        subscriber.assertValues();
        state.apply(it -> it + 1);
        scheduler.triggerActions();
        subscriber.assertValues(0, 1);
    }
}