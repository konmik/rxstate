package util.rx;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

import static rx.functions.Actions.empty;

public class RxState<T> {

    public enum StartWith {
        /**
         * No start values will be emitted.
         */
        NO,

        /**
         * Current value will be emitted immediately on the subscription thread.
         * Do this only if you're subscribing already on the RxState scheduler.
         */
        IMMEDIATE,

        /**
         * Current value will be emitted on the RxState scheduler.
         */
        SCHEDULE
    }

    private final Scheduler scheduler;
    private final ConcurrentLinkedQueue<Entry<T>> queue = new ConcurrentLinkedQueue<>();
    private final List<Subscriber<? super T>> subscribers = new CopyOnWriteArrayList<>();

    private volatile T value;
    private volatile boolean emitting;

    public RxState(T initialValue, Scheduler scheduler) {
        this.value = initialValue;
        this.scheduler = scheduler;
    }

    /**
     * Apply a function to the value.
     * <p>
     * The passed function receives the current value and must produce a new value
     * (or can just return the same).
     * <p>
     * The function must be pure, because it can be called several times
     * depending on simultaneous updates of the value (in future implementations).
     * <p>
     * The function should also be fast to take less synchronized time.
     */
    public void apply(Func1<T, T> func) {

        synchronized (this) {
            value = func.call(value);
            for (Subscriber<? super T> subscriber : subscribers) {
                queue.add(new Entry<>(subscriber, value));
            }
        }

        emit();
    }

    /**
     * Observable of sequential value changes.
     */
    public Observable<T> values(StartWith startWith) {
        return Observable
                .create(subscriber -> {
                    T emit = null;
                    boolean doEmit = false;
                    boolean added = false; // we add subscriber only at the moment when we want to start receiving items
                    synchronized (this) {
                        if (startWith == StartWith.IMMEDIATE) {
                            if (emitting) {
                                queue.add(new Entry<>(subscriber, value));
                                subscribers.add(subscriber);
                                added = true;
                            } else {
                                emit = value;
                                doEmit = true;
                            }
                        }
                    }
                    if (doEmit) {
                        subscriber.onNext(emit);
                    }
                    synchronized (this) {
                        if (!added) {
                            subscribers.add(subscriber);
                        }
                        if (startWith == StartWith.SCHEDULE) {
                            queue.add(new Entry<>(subscriber, value));
                        }
                    }
                    subscriber.add(Subscriptions.create(() -> {
                        synchronized (RxState.this) {
                            subscribers.remove(subscriber);
                        }
                    }));
                    emit();
                });
    }

    /**
     * A check if there are values to be emitted.
     */
    public boolean isEmitting() {
        synchronized (this) {
            return emitting || !queue.isEmpty();
        }
    }

    /**
     * Use this function only if you guarantee that no other functions are trying to modify the value
     * the same time, otherwise you're asking for race conditions.
     * <p>
     * 1. RxValue can be still emitting previous values on the scheduler.
     * 2. Concurrent {@link #apply(Func1)} can be called.
     */
    public T value() {
        return value;
    }

    private void emit() {
        Scheduler.Worker worker = scheduler.createWorker();
        worker.schedule(() -> {
            emitLoop();
            worker.unsubscribe();
        });
    }

    private void emitLoop() {

        synchronized (this) {
            if (emitting) {
                return;
            }
            emitting = true;
        }

        for (; ; ) {

            Entry<T> entry;
            synchronized (this) {
                if (queue.isEmpty()) {
                    emitting = false;
                    return;
                }
                entry = queue.poll();
            }

            entry.subscriber.onNext(entry.value);
        }
    }

    private static class Entry<T> {
        final Subscriber<? super T> subscriber;
        final T value;

        private Entry(Subscriber<? super T> subscriber, T value) {
            this.subscriber = subscriber;
            this.value = value;
        }
    }

    /**
     * This is the random delay function that can be set from the test.
     * It must be called after the each function step.
     */
    static Action0 raceTestDelay = empty();
}
