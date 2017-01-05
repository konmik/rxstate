package util.rx2;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Function;

import static io.reactivex.exceptions.Exceptions.propagate;

@SuppressWarnings("Duplicates")
public class RxState<T> {

    private final Scheduler scheduler;
    private final ConcurrentLinkedQueue<Entry<T>> queue = new ConcurrentLinkedQueue<>();
    private final List<ObservableEmitter<T>> emitters = new CopyOnWriteArrayList<>();

    private volatile T value;
    private volatile boolean emitting;

    public RxState(T initialValue, Scheduler scheduler) {
        this.value = initialValue;
        this.scheduler = scheduler;
    }

    /**
     * Applies a function to the current value, emitting the new returned value.
     * <p>
     * The function should fast to take less synchronized time.
     */
    public void apply(Function<T, T> func) {

        synchronized (this) {
            try {
                value = func.apply(value);
                for (ObservableEmitter<T> emitter : emitters) {
                    queue.add(new Entry<>(emitter, value));
                }
            } catch (Exception e) {
                throw propagate(e);
            }
        }

        emit();
    }

    /**
     * Observable of sequential value changes.
     */
    public Observable<T> values(StartWith startWith) {
        return Observable.create(emitter -> {
            if (startWith == StartWith.IMMEDIATE) {
                onSubscribeImmediate(emitter);
            } else if (startWith == StartWith.SCHEDULE) {
                onSubscribeSchedule(emitter);
            } else {
                onSubscribeNo(emitter);
            }
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
     * 2. Concurrent {@link #apply(Function)} can be called.
     */
    public T value() {
        return value;
    }

    private void onSubscribeNo(ObservableEmitter< T> emitter) {
        synchronized (this) {
            emitters.add(emitter);
        }
        setDisposable(emitter);
    }

    private void onSubscribeSchedule(ObservableEmitter<T> emitter) {
        synchronized (this) {
            emitters.add(emitter);
            queue.add(new Entry<>(emitter, value));
        }
        setDisposable(emitter);
        emit();
    }

    private void onSubscribeImmediate(ObservableEmitter<T> emitter) {
        T emit;
        synchronized (this) {
            emit = value;
        }
        emitter.onNext(emit);
        synchronized (this) {
            emitters.add(emitter);
        }
        setDisposable(emitter);
    }

    private void setDisposable(ObservableEmitter<T> emitter) {
        emitter.setDisposable(Disposables.fromAction(() -> {
            synchronized (this) {
                emitters.remove(emitter);
            }
        }));
    }

    private void emit() {
        Scheduler.Worker worker = scheduler.createWorker();
        worker.schedule(() -> {
            emitLoop();
            worker.dispose();
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
            Entry<T> next;
            synchronized (this) {
                if (queue.isEmpty()) {
                    emitting = false;
                    return;
                }
                next = queue.poll();
            }

            next.emitter.onNext(next.value);
        }
    }

    private static class Entry<T> {
        final ObservableEmitter<T> emitter;
        final T value;

        private Entry(ObservableEmitter<T> emitter, T value) {
            this.emitter = emitter;
            this.value = value;
        }
    }
}
