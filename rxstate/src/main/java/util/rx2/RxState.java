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

public class RxState<T> {

    private final Scheduler scheduler;
    private final ConcurrentLinkedQueue<Entry<T>> queue = new ConcurrentLinkedQueue<>();
    private final List<ObservableEmitter<T>> emitters = new CopyOnWriteArrayList<>();

    private volatile T value;
    private volatile boolean emitting;

    private static class Entry<T> {
        final ObservableEmitter<T> emitter;
        final T value;

        private Entry(ObservableEmitter<T> emitter, T value) {
            this.emitter = emitter;
            this.value = value;
        }
    }

    public RxState(T initialValue, Scheduler scheduler) {
        this.value = initialValue;
        this.scheduler = scheduler;
    }

    public void apply(Function<T, T> func) {

        synchronized (this) {
            try {
                value = func.apply(value);
                for (ObservableEmitter<T> subscriber : emitters) {
                    queue.add(new Entry<>(subscriber, value));
                }
            } catch (Exception e) {
                throw propagate(e);
            }
        }

        emit();
    }

    public Observable<T> values() {
        return values(true);
    }

    public Observable<T> values(boolean emit) {
        return Observable.create(emitter -> {
            synchronized (this) {
                emitters.add(emitter);
                if (emit) {
                    queue.add(new Entry<>(emitter, value));
                }
            }
            emitter.setDisposable(Disposables.fromAction(() -> emitters.remove(emitter)));
            emit();
        });
    }

    public Observable<T> valuesStartImmediate() {
        return values(false)
                .startWith(value);
    }

    public boolean isEmitting() {
        synchronized (this) {
            return emitting || !queue.isEmpty();
        }
    }

    public T value() {
        return value;
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

    private void emit() {
        Scheduler.Worker worker = scheduler.createWorker();
        worker.schedule(() -> {
            emitLoop();
            worker.dispose();
        });
    }
}
