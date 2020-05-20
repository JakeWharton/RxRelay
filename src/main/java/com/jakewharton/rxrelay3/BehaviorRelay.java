/**
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package com.jakewharton.rxrelay3;

import com.jakewharton.rxrelay3.AppendOnlyLinkedArrayList.NonThrowingPredicate;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.annotations.CheckReturnValue;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.annotations.Nullable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Relay that emits the most recent item it has observed and all subsequent observed items to each subscribed
 * {@link Observer}.
 * <p>
 * <img width="640" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/S.BehaviorSubject.png" alt="">
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code

  // observer will receive all events.
  BehaviorRelay<Object> subject = BehaviorRelay.createDefault("default");
  subject.subscribe(observer);
  subject.accept("one");
  subject.accept("two");
  subject.accept("three");

  // observer will receive the "one", "two" and "three" events, but not "zero"
  BehaviorRelay<Object> subject = BehaviorRelay.createDefault("default");
  subject.accept("zero");
  subject.accept("one");
  subject.subscribe(observer);
  subject.accept("two");
  subject.accept("three");
  } </pre>
 */
public final class BehaviorRelay<T> extends Relay<T> {

    /** An empty array to avoid allocation in getValues(). */
    private static final Object[] EMPTY_ARRAY = new Object[0];

    final AtomicReference<T> value;

    final AtomicReference<BehaviorDisposable<T>[]> subscribers;

    @SuppressWarnings("rawtypes")
    static final BehaviorDisposable[] EMPTY = new BehaviorDisposable[0];

    final Lock readLock;
    final Lock writeLock;

    long index;

    /**
     * Creates a {@link BehaviorRelay} without a default item.
     */
    @CheckReturnValue
    @NonNull
    public static <T> BehaviorRelay<T> create() {
        return new BehaviorRelay<T>();
    }

    /**
     * Creates a {@link BehaviorRelay} that emits the last item it observed and all subsequent items to each
     * {@link Observer} that subscribes to it.
     *
     * @param defaultValue
     *            the item that will be emitted first to any {@link Observer} as long as the
     *            {@link BehaviorRelay} has not yet observed any items from its source {@code Observable}
     */
    @CheckReturnValue
    @NonNull
    public static <T> BehaviorRelay<T> createDefault(@NonNull T defaultValue) {
        return new BehaviorRelay<T>(defaultValue);
    }

    /**
     * Constructs an empty BehaviorRelay.
     */
    @SuppressWarnings("unchecked")
    BehaviorRelay() {
        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
        this.subscribers = new AtomicReference<BehaviorDisposable<T>[]>(EMPTY);
        this.value = new AtomicReference<T>();
    }

    /**
     * Constructs a BehaviorRelay with the given initial value.
     * @param defaultValue the initial value, not null (verified)
     * @throws NullPointerException if {@code defaultValue} is null
     */
    BehaviorRelay(@NonNull T defaultValue) {
        this();
        if (defaultValue == null) throw new NullPointerException("defaultValue == null");
        value.lazySet(defaultValue);
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        BehaviorDisposable<T> bs = new BehaviorDisposable<T>(observer, this);
        observer.onSubscribe(bs);
        add(bs);
        if (bs.cancelled) {
            remove(bs);
        } else {
            bs.emitFirst();
        }
    }

    @Override
    public void accept(@NonNull T value) {
        if (value == null) throw new NullPointerException("value == null");

        setCurrent(value);
        for (BehaviorDisposable<T> bs : subscribers.get()) {
            bs.emitNext(value, index);
        }
    }

    @Override
    public boolean hasObservers() {
        return subscribers.get().length != 0;
    }

    /* test support*/ int subscriberCount() {
        return subscribers.get().length;
    }

    /**
     * Returns a single value the Relay currently has or null if no such value exists.
     * <p>The method is thread-safe.
     */
    @Nullable
    public T getValue() {
        return value.get();
    }

    /**
     * Returns an Object array containing snapshot all values of the Relay.
     * <p>The method is thread-safe.
     * @deprecated in 2.1; put the result of {@link #getValue()} into an array manually, will be removed in 3.x
     */
    @Deprecated
    public Object[] getValues() {
        @SuppressWarnings("unchecked")
        T[] a = (T[])EMPTY_ARRAY;
        T[] b = getValues(a);
        if (b == EMPTY_ARRAY) {
            return new Object[0];
        }
        return b;

    }

    /**
     * Returns a typed array containing a snapshot of all values of the Relay.
     * <p>The method follows the conventions of Collection.toArray by setting the array element
     * after the last value to null (if the capacity permits).
     * <p>The method is thread-safe.
     * @param array the target array to copy values into if it fits
     * @deprecated in 2.1; put the result of {@link #getValue()} into an array manually, will be removed in 3.x
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public T[] getValues(T[] array) {
        T o = value.get();
        if (o == null) {
            if (array.length != 0) {
                array[0] = null;
            }
            return array;
        }
        if (array.length != 0) {
            array[0] = o;
            if (array.length != 1) {
                array[1] = null;
            }
        } else {
            array = (T[]) Array.newInstance(array.getClass().getComponentType(), 1);
            array[0] = o;
        }
        return array;
    }

    /**
     * Returns true if the subject has any value.
     * <p>The method is thread-safe.
     * @return true if the subject has any value
     */
    public boolean hasValue() {
        return value.get() != null;
    }

    void add(BehaviorDisposable<T> rs) {
        for (;;) {
            BehaviorDisposable<T>[] a = subscribers.get();
            int len = a.length;
            @SuppressWarnings("unchecked")
            BehaviorDisposable<T>[] b = new BehaviorDisposable[len + 1];
            System.arraycopy(a, 0, b, 0, len);
            b[len] = rs;
            if (subscribers.compareAndSet(a, b)) {
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    void remove(BehaviorDisposable<T> rs) {
        for (;;) {
            BehaviorDisposable<T>[] a = subscribers.get();
            int len = a.length;
            if (len == 0) {
                return;
            }
            int j = -1;
            for (int i = 0; i < len; i++) {
                if (a[i] == rs) {
                    j = i;
                    break;
                }
            }

            if (j < 0) {
                return;
            }
            BehaviorDisposable<T>[] b;
            if (len == 1) {
                b = EMPTY;
            } else {
                b = new BehaviorDisposable[len - 1];
                System.arraycopy(a, 0, b, 0, j);
                System.arraycopy(a, j + 1, b, j, len - j - 1);
            }
            if (subscribers.compareAndSet(a, b)) {
                return;
            }
        }
    }

    void setCurrent(@NonNull T o) {
        writeLock.lock();
        index++;
        value.lazySet(o);
        writeLock.unlock();
    }

    static final class BehaviorDisposable<T> implements Disposable, NonThrowingPredicate<T> {

        final Observer<? super T> downstream;
        final BehaviorRelay<T> state;

        boolean next;
        boolean emitting;
        AppendOnlyLinkedArrayList<T> queue;

        boolean fastPath;

        volatile boolean cancelled;

        long index;

        BehaviorDisposable(Observer<? super T> actual, BehaviorRelay<T> state) {
            this.downstream = actual;
            this.state = state;
        }

        @Override
        public void dispose() {
            if (!cancelled) {
                cancelled = true;

                state.remove(this);
            }
        }

        @Override
        public boolean isDisposed() {
            return cancelled;
        }

        void emitFirst() {
            if (cancelled) {
                return;
            }
            T o;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                if (next) {
                    return;
                }

                BehaviorRelay<T> s = state;
                Lock lock = s.readLock;

                lock.lock();
                index = s.index;
                o = s.value.get();
                lock.unlock();

                emitting = o != null;
                next = true;
            }

            if (o != null) {
                test(o);
                emitLoop();
            }
        }

        void emitNext(T value, long stateIndex) {
            if (cancelled) {
                return;
            }
            if (!fastPath) {
                synchronized (this) {
                    if (cancelled) {
                        return;
                    }
                    if (index == stateIndex) {
                        return;
                    }
                    if (emitting) {
                        AppendOnlyLinkedArrayList<T> q = queue;
                        if (q == null) {
                            q = new AppendOnlyLinkedArrayList<T>(4);
                            queue = q;
                        }
                        q.add(value);
                        return;
                    }
                    next = true;
                }
                fastPath = true;
            }

            test(value);
        }

        @Override
        public boolean test(T o) {
            if (!cancelled) {
                downstream.onNext(o);
            }
            return false;
        }

        void emitLoop() {
            for (;;) {
                if (cancelled) {
                    return;
                }
                AppendOnlyLinkedArrayList<T> q;
                synchronized (this) {
                    q = queue;
                    if (q == null) {
                        emitting = false;
                        return;
                    }
                    queue = null;
                }

                q.forEachWhile(this);
            }
        }
    }
}
