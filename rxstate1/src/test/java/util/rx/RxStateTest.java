package util.rx;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private static final int MAX_DURATION = (int) TimeUnit.SECONDS.toMillis(60);
    private static final int THREADS_NUMBER = 20;

    long preventOptimization;

    @Test
    public void raceTest() throws Exception {
        AtomicLong delayMultiplier = new AtomicLong();
        initRandomDelay(delayMultiplier);

        AtomicLong done = new AtomicLong();

        Racer.run(MAX_ITERATIONS, MAX_DURATION, iterationNumber -> {

            // these are magic numbers, change them depending on amount of iterations
            // amount of iterations with these numbers must be about 10% smaller than with zero multiplier
            applyRandomDelay(delayMultiplier, iterationNumber);

            Scheduler immediate = Schedulers.immediate();
            Scheduler singleThread = Schedulers.from(Executors.newSingleThreadExecutor());
            List<Scheduler> schedulers = asList(
                    immediate, Schedulers.io(),
                    singleThread,
                    Schedulers.computation(), Schedulers.newThread());
            int schedulerI = iterationNumber % schedulers.size();
            Scheduler scheduler = schedulers.get(schedulerI);

            RxState<Integer> value = new RxState<>(0, scheduler);

            List<TestSubscriber<Integer>> subscribers = synchronizedList(new ArrayList<>());
            List<StartWith> startWiths = synchronizedList(new ArrayList<>());

            if (scheduler == immediate || scheduler == singleThread) {
                TestSubscriber<Integer> subscriber = new TestSubscriber<>();
                subscribers.add(subscriber);
                startWiths.add(StartWith.IMMEDIATE);
                value.values(StartWith.IMMEDIATE).subscribe(subscriber);
            }

            List<List<Runnable>> threads = new ArrayList<>();
            for (int i = 0; i < THREADS_NUMBER; i++) {
                int finalI = i;
                threads.add(asList(
                        () -> {
                            StartWith startWith = StartWith.values()[(iterationNumber + finalI) % (StartWith.values().length - 1) + 1];
                            startWith = startWith == StartWith.IMMEDIATE ? StartWith.SCHEDULE : startWith;
                            TestSubscriber<Integer> subscriber2 = new TestSubscriber<>();
                            subscribers.add(subscriber2);
                            startWiths.add(startWith);
                            value.values(startWith).subscribe(subscriber2);
                        },
                        () -> value.apply(it -> it + 1),
                        () -> value.apply(it -> it + 1),
                        () -> value.apply(it -> it + 1)));
            }

            race(threads);

            while (value.isEmitting()) {
            }

            verifySubscribers(subscribers, startWiths, schedulerI);

            done.incrementAndGet();
        });

        System.out.println(format(Locale.US,
                "iterations: %d, ignore: %d",
                done.get(), preventOptimization));
    }

    private void verifySubscribers(List<TestSubscriber<Integer>> subscribers, List<StartWith> startWiths, int schedulerI) {
        for (int s = 0; s < subscribers.size(); s++) {
            TestSubscriber<Integer> subscriber1 = subscribers.get(s);
            List<Integer> values = subscriber1.getOnNextEvents();
            if (values.size() > 0) {
                ArrayList<Integer> expected = new ArrayList<>(values.size());
                for (int i = 0, v = values.get(0); i < values.size(); i++, v++) {
                    expected.add(v);
                }
                assertEquals("startWith: " + startWiths.get(s) + ", scheduler: " + schedulerI, expected, values);
            }
        }
    }

    private void initRandomDelay(AtomicLong delayMultiplier) {
        Random random = new Random();
        RxState.raceTestDelay = () -> {
            double target = random.nextDouble() * delayMultiplier.get();
            for (int i = 0; i < target; i++) {
                preventOptimization++;
            }
        };
    }

    private void applyRandomDelay(AtomicLong delayMultiplier, int iterationNumber) {
        delayMultiplier.set(1000 + iterationNumber % 10000);
    }

    @Test
    public void startImmediate() {
        RxState<Integer> state = new RxState<>(0, Schedulers.immediate());
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        state.values(StartWith.IMMEDIATE).subscribe(subscriber);
        subscriber.assertValues(0);
        state.apply(it -> it + 1);
        subscriber.assertValues(0, 1);
    }

    @Test
    public void startNo() {
        RxState<Integer> state = new RxState<>(0, Schedulers.immediate());
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        state.values(StartWith.NO).subscribe(subscriber);
        subscriber.assertValues();
        state.apply(it -> it + 1);
        subscriber.assertValues(1);
    }

    @Test
    public void startSchedule() {
        TestScheduler scheduler = new TestScheduler();
        RxState<Integer> state = new RxState<>(0, scheduler);
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        state.values(StartWith.SCHEDULE).subscribe(subscriber);
        subscriber.assertValues();
        state.apply(it -> it + 1);
        scheduler.triggerActions();
        subscriber.assertValues(0, 1);
    }
}