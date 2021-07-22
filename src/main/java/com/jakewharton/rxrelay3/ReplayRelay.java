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

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.annotations.CheckReturnValue;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.annotations.Nullable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Replays events to Observers.
 * <p>
 * <img width="640" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/S.ReplaySubject.png" alt="">
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code

  ReplayRelay<Object> relay = new ReplayRelay<>();
  relay.accept("one");
  relay.accept("two");
  relay.accept("three");

  // both of the following will get the values from above
  relay.subscribe(observer1);
  relay.subscribe(observer2);

  } </pre>
 *
 * @param <T> the value type
 */
public final class ReplayRelay<T> extends Relay<T> {
    final ReplayBuffer<T> buffer;

    final AtomicReference<ReplayDisposable<T>[]> observers;

    @SuppressWarnings("rawtypes")
    static final ReplayDisposable[] EMPTY = new ReplayDisposable[0];

    /**
     * Creates an unbounded replay relay.
     * <p>
     * The internal buffer is backed by an {@link ArrayList} and starts with an initial capacity of 16. Once the
     * number of items reaches this capacity, it will grow as necessary (usually by 50%). However, as the
     * number of items grows, this causes frequent array reallocation and copying, and may hurt performance
     * and latency. This can be avoided with the {@link #create(int)} overload which takes an initial capacity
     * parameter and can be tuned to reduce the array reallocation frequency as needed.
     */
    @CheckReturnValue
    @NonNull
    public static <T> ReplayRelay<T> create() {
        return new ReplayRelay<T>(new UnboundedReplayBuffer<T>(16));
    }

    /**
     * Creates an unbounded replay relay with the specified initial buffer capacity.
     * <p>
     * Use this method to avoid excessive array reallocation while the internal buffer grows to accommodate new
     * items. For example, if you know that the buffer will hold 32k items, you can ask the
     * {@code ReplayRelay} to preallocate its internal array with a capacity to hold that many items. Once
     * the items start to arrive, the internal array won't need to grow, creating less garbage and no overhead
     * due to frequent array-copying.
     *
     * @param capacityHint
     *          the initial buffer capacity
     */
    @CheckReturnValue
    @NonNull
    public static <T> ReplayRelay<T> create(int capacityHint) {
        return new ReplayRelay<T>(new UnboundedReplayBuffer<T>(capacityHint));
    }

    /**
     * Creates a size-bounded replay relay.
     * <p>
     * In this setting, the {@code ReplayRelay} holds at most {@code size} items in its internal buffer and
     * discards the oldest item.
     * <p>
     * When observers subscribe to a terminated {@code ReplayRelay}, they are guaranteed to see at most
     * {@code size} {@code onNext} events followed by a termination event.
     * <p>
     * If an observer subscribes while the {@code ReplayRelay} is active, it will observe all items in the
     * buffer at that point in time and each item observed afterwards, even if the buffer evicts items due to
     * the size constraint in the mean time. In other words, once an Observer subscribes, it will receive items
     * without gaps in the sequence.
     *
     * @param maxSize
     *          the maximum number of buffered items
     */
    @CheckReturnValue
    @NonNull
    public static <T> ReplayRelay<T> createWithSize(int maxSize) {
        return new ReplayRelay<T>(new SizeBoundReplayBuffer<T>(maxSize));
    }

    /**
     * Creates an unbounded replay replay with the bounded-implementation for testing purposes.
     * <p>
     * This variant behaves like the regular unbounded {@code ReplayRelay} created via {@link #create()} but
     * uses the structures of the bounded-implementation. This is by no means intended for the replacement of
     * the original, array-backed and unbounded {@code ReplayRelay} due to the additional overhead of the
     * linked-list based internal buffer. The sole purpose is to allow testing and reasoning about the behavior
     * of the bounded implementations without the interference of the eviction policies.
     */
    /* test */ static <T> ReplayRelay<T> createUnbounded() {
        return new ReplayRelay<T>(new SizeBoundReplayBuffer<T>(Integer.MAX_VALUE));
    }

    /**
     * Creates a time-bounded replay relay.
     * <p>
     * In this setting, the {@code ReplayRelay} internally tags each observed item with a timestamp value
     * supplied by the {@link Scheduler} and keeps only those whose age is less than the supplied time value
     * converted to milliseconds. For example, an item arrives at T=0 and the max age is set to 5; at T&gt;=5
     * this first item is then evicted by any subsequent item or termination event, leaving the buffer empty.
     * <p>
     * Once the subject is terminated, observers subscribing to it will receive items that remained in the
     * buffer after the terminal event, regardless of their age.
     * <p>
     * If an observer subscribes while the {@code ReplayRelay} is active, it will observe only those items
     * from within the buffer that have an age less than the specified time, and each item observed thereafter,
     * even if the buffer evicts items due to the time constraint in the mean time. In other words, once an
     * observer subscribes, it observes items without gaps in the sequence except for any outdated items at the
     * beginning of the sequence.
     * <p>
     * Note that terminal notifications ({@code onError} and {@code onComplete}) trigger eviction as well. For
     * example, with a max age of 5, the first item is observed at T=0, then an {@code onComplete} notification
     * arrives at T=10. If an observer subscribes at T=11, it will find an empty {@code ReplayRelay} with just
     * an {@code onComplete} notification.
     *
     * @param maxAge
     *          the maximum age of the contained items
     * @param unit
     *          the time unit of {@code time}
     * @param scheduler
     *          the {@link Scheduler} that provides the current time
     */
    @CheckReturnValue
    @NonNull
    public static <T> ReplayRelay<T> createWithTime(long maxAge, TimeUnit unit, Scheduler scheduler) {
        return new ReplayRelay<T>(new SizeAndTimeBoundReplayBuffer<T>(Integer.MAX_VALUE, maxAge, unit, scheduler));
    }

    /**
     * Creates a time- and size-bounded replay subject.
     * <p>
     * In this setting, the {@code ReplayRelay} internally tags each received item with a timestamp value
     * supplied by the {@link Scheduler} and holds at most {@code size} items in its internal buffer. It evicts
     * items from the start of the buffer if their age becomes less-than or equal to the supplied age in
     * milliseconds or the buffer reaches its {@code size} limit.
     * <p>
     * When observers subscribe to a terminated {@code ReplayRelay}, they observe the items that remained in
     * the buffer after the terminal notification, regardless of their age, but at most {@code size} items.
     * <p>
     * If an observer subscribes while the {@code ReplayRelay} is active, it will observe only those items
     * from within the buffer that have age less than the specified time and each subsequent item, even if the
     * buffer evicts items due to the time constraint in the mean time. In other words, once an observer
     * subscribes, it observes items without gaps in the sequence except for the outdated items at the beginning
     * of the sequence.
     * <p>
     * Note that terminal notifications ({@code onError} and {@code onComplete}) trigger eviction as well. For
     * example, with a max age of 5, the first item is observed at T=0, then an {@code onComplete} notification
     * arrives at T=10. If an observer subscribes at T=11, it will find an empty {@code ReplayRelay} with just
     * an {@code onComplete} notification.
     *
     * @param maxAge
     *          the maximum age of the contained items
     * @param unit
     *          the time unit of {@code time}
     * @param maxSize
     *          the maximum number of buffered items
     * @param scheduler
     *          the {@link Scheduler} that provides the current time
     */
    @CheckReturnValue
    @NonNull
    public static <T> ReplayRelay<T> createWithTimeAndSize(long maxAge, TimeUnit unit, Scheduler scheduler, int maxSize) {
        return new ReplayRelay<T>(new SizeAndTimeBoundReplayBuffer<T>(maxSize, maxAge, unit, scheduler));
    }

    /**
     * Constructs a ReplayRelay with the given custom ReplayBuffer instance.
     * @param buffer the ReplayBuffer instance, not null (not verified)
     */
    @SuppressWarnings("unchecked") ReplayRelay(ReplayBuffer<T> buffer) {
        this.buffer = buffer;
        this.observers = new AtomicReference<ReplayDisposable<T>[]>(EMPTY);
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        ReplayDisposable<T> rs = new ReplayDisposable<T>(observer, this);
        observer.onSubscribe(rs);

        if (!rs.cancelled) {
            if (add(rs)) {
                if (rs.cancelled) {
                    remove(rs);
                    return;
                }
            }
            buffer.replay(rs);
        }
    }

    @Override
    public void accept(@NonNull T value) {
        if (value == null) throw new NullPointerException("value == null");

        ReplayBuffer<T> b = buffer;
        b.add(value);

        for (ReplayDisposable<T> rs : observers.get()) {
            b.replay(rs);
        }
    }

    @Override
    public boolean hasObservers() {
        return observers.get().length != 0;
    }

    /* test */ int observerCount() {
        return observers.get().length;
    }

    /**
     * Returns a single value the Relay currently has or null if no such value exists.
     * <p>The method is thread-safe.
     */
    @Nullable
    public T getValue() {
        return buffer.getValue();
    }

    /**
     * Makes sure the item cached by the head node in a bounded
     * ReplayRelay is released (as it is never part of a replay).
     * <p>
     * By default, live bounded buffers will remember one item before
     * the currently receivable one to ensure subscribers can always
     * receive a continuous sequence of items. A terminated ReplaySubject
     * automatically releases this inaccessible item.
     * <p>
     * The method must be called sequentially, similar to the standard
     * {@code onXXX} methods.
     * @since 2.1
     */
    public void cleanupBuffer() {
        buffer.trimHead();
    }

    /** An empty array to avoid allocation in getValues(). */
    private static final Object[] EMPTY_ARRAY = new Object[0];

    /**
     * Returns an Object array containing snapshot all values of the Relay.
     * <p>The method is thread-safe.
     */
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
     */
    public T[] getValues(T[] array) {
        return buffer.getValues(array);
    }

    /**
     * Returns true if the relay has any value.
     * <p>The method is thread-safe.
     */
    public boolean hasValue() {
        return buffer.size() != 0; // NOPMD
    }

    /* test*/ int size() {
        return buffer.size();
    }

    boolean add(ReplayDisposable<T> rs) {
        for (;;) {
            ReplayDisposable<T>[] a = observers.get();
            int len = a.length;
            @SuppressWarnings("unchecked")
            ReplayDisposable<T>[] b = new ReplayDisposable[len + 1];
            System.arraycopy(a, 0, b, 0, len);
            b[len] = rs;
            if (observers.compareAndSet(a, b)) {
                return true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    void remove(ReplayDisposable<T> rs) {
        for (;;) {
            ReplayDisposable<T>[] a = observers.get();
            if (a == EMPTY) {
                return;
            }
            int len = a.length;
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
            ReplayDisposable<T>[] b;
            if (len == 1) {
                b = EMPTY;
            } else {
                b = new ReplayDisposable[len - 1];
                System.arraycopy(a, 0, b, 0, j);
                System.arraycopy(a, j + 1, b, j, len - j - 1);
            }
            if (observers.compareAndSet(a, b)) {
                return;
            }
        }
    }

    /**
     * Abstraction over a buffer that receives events and replays them to
     * individual Observers.
     */
    interface ReplayBuffer<T> {

        void add(T value);

        void replay(ReplayDisposable<T> rs);

        int size();

        @Nullable
        T getValue();

        T[] getValues(T[] array);

        void trimHead();
    }

    static final class ReplayDisposable<T> extends AtomicInteger implements Disposable {

        private static final long serialVersionUID = 466549804534799122L;
        final Observer<? super T> downstream;
        final ReplayRelay<T> state;

        Object index;

        volatile boolean cancelled;

        ReplayDisposable(Observer<? super T> actual, ReplayRelay<T> state) {
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
    }

    static final class UnboundedReplayBuffer<T>
    extends AtomicReference<Object>
    implements ReplayBuffer<T> {

        private static final long serialVersionUID = -733876083048047795L;

        final List<T> buffer;

        volatile int size;

        UnboundedReplayBuffer(int capacityHint) {
            if (capacityHint <= 0) throw new IllegalArgumentException("capacityHint > 0 required but it was " + capacityHint);
            this.buffer = new ArrayList<T>(capacityHint);
        }

        @Override
        public void add(T value) {
            buffer.add(value);
            size++;
        }

        @Override
        public void trimHead() {
            // no-op in this type of buffer
        }

        @Override
        @Nullable
        @SuppressWarnings("unchecked")
        public T getValue() {
            int s = size;
            if (s != 0) {
                return buffer.get(s - 1);
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T[] getValues(T[] array) {
            int s = size;
            if (s == 0) {
                if (array.length != 0) {
                    array[0] = null;
                }
                return array;
            }

            if (array.length < s) {
                array = (T[]) Array.newInstance(array.getClass().getComponentType(), s);
            }
            List<T> b = buffer;
            for (int i = 0; i < s; i++) {
                array[i] = b.get(i);
            }
            if (array.length > s) {
                array[s] = null;
            }

            return array;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void replay(ReplayDisposable<T> rs) {
            if (rs.getAndIncrement() != 0) {
                return;
            }

            int missed = 1;
            final List<T> b = buffer;
            final Observer<? super T> a = rs.downstream;

            Integer indexObject = (Integer)rs.index;
            int index;
            if (indexObject != null) {
                index = indexObject;
            } else {
                index = 0;
                rs.index = 0;
            }

            for (;;) {

                if (rs.cancelled) {
                    rs.index = null;
                    return;
                }

                int s = size;

                while (s != index) {

                    if (rs.cancelled) {
                        rs.index = null;
                        return;
                    }

                    T o = b.get(index);

                    a.onNext(o);
                    index++;
                }

                if (index != size) {
                    continue;
                }

                rs.index = index;

                missed = rs.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

        @Override
        public int size() {
            return size;
        }
    }

    static final class Node<T> extends AtomicReference<Node<T>> {

        private static final long serialVersionUID = 6404226426336033100L;

        final T value;

        Node(T value) {
            this.value = value;
        }
    }

    static final class TimedNode<T> extends AtomicReference<TimedNode<T>> {

        private static final long serialVersionUID = 6404226426336033100L;

        final T value;
        final long time;

        TimedNode(T value, long time) {
            this.value = value;
            this.time = time;
        }
    }

    static final class SizeBoundReplayBuffer<T>
    extends AtomicReference<Object>
    implements ReplayBuffer<T> {

        private static final long serialVersionUID = 1107649250281456395L;

        final int maxSize;
        int size;

        volatile Node<T> head;

        Node<T> tail;

        SizeBoundReplayBuffer(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize > 0 required but it was " + maxSize);
            }
            this.maxSize = maxSize;
            Node<T> h = new Node<T>(null);
            this.tail = h;
            this.head = h;
        }

        void trim() {
            if (size > maxSize) {
                size--;
                Node<T> h = head;
                head = h.get();
            }
        }

        @Override
        public void add(T value) {
            Node<T> n = new Node<T>(value);
            Node<T> t = tail;

            tail = n;
            size++;
            t.set(n); // releases both the tail and size

            trim();
        }

        /**
         * Replace a non-empty head node with an empty one to
         * allow the GC of the inaccessible old value.
         */
        @Override
        public void trimHead() {
            Node<T> h = head;
            if (h.value != null) {
                Node<T> n = new Node<T>(null);
                n.lazySet(h.get());
                head = n;
            }
        }

        @Override
        @Nullable
        @SuppressWarnings("unchecked")
        public T getValue() {
            Node<T> h = head;

            for (;;) {
                Node<T> next = h.get();
                if (next == null) {
                    break;
                }
                h = next;
            }

            return h.value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T[] getValues(T[] array) {
            Node<T> h = head;
            int s = size();

            if (s == 0) {
                if (array.length != 0) {
                    array[0] = null;
                }
            } else {
                if (array.length < s) {
                    array = (T[]) Array.newInstance(array.getClass().getComponentType(), s);
                }

                int i = 0;
                while (i != s) {
                    Node<T> next = h.get();
                    array[i] = next.value;
                    i++;
                    h = next;
                }
                if (array.length > s) {
                    array[s] = null;
                }
            }

            return array;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void replay(ReplayDisposable<T> rs) {
            if (rs.getAndIncrement() != 0) {
                return;
            }

            int missed = 1;
            final Observer<? super T> a = rs.downstream;

            Node<T> index = (Node<T>)rs.index;
            if (index == null) {
                index = head;
            }

            for (;;) {

                for (;;) {
                    if (rs.cancelled) {
                        rs.index = null;
                        return;
                    }

                    Node<T> n = index.get();

                    if (n == null) {
                        break;
                    }

                    a.onNext(n.value);

                    index = n;
                }

                if (index.get() != null) {
                    continue;
                }

                rs.index = index;

                missed = rs.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

        @Override
        public int size() {
            int s = 0;
            Node<T> h = head;
            while (s != Integer.MAX_VALUE) {
                Node<T> next = h.get();
                if (next == null) {
                    break;
                }
                s++;
                h = next;
            }

            return s;
        }
    }

    static final class SizeAndTimeBoundReplayBuffer<T>
    extends AtomicReference<Object>
    implements ReplayBuffer<T> {

        private static final long serialVersionUID = -8056260896137901749L;

        final int maxSize;
        final long maxAge;
        final TimeUnit unit;
        final Scheduler scheduler;
        int size;

        volatile TimedNode<T> head;

        TimedNode<T> tail;

        SizeAndTimeBoundReplayBuffer(int maxSize, long maxAge, TimeUnit unit, Scheduler scheduler) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize > 0 required but it was " + maxSize);
            }
            if (maxAge <= 0) {
                throw new IllegalArgumentException("maxAge > 0 required but it was " + maxAge);
            }
            if (unit == null) throw new NullPointerException("unit == null");
            if (scheduler == null) throw new NullPointerException("scheduler == null");
            this.maxSize = maxSize;
            this.maxAge = maxAge;
            this.unit = unit;
            this.scheduler = scheduler;
            TimedNode<T> h = new TimedNode<T>(null, 0L);
            this.tail = h;
            this.head = h;
        }

        void trim() {
            if (size > maxSize) {
                size--;
                TimedNode<T> h = head;
                head = h.get();
            }
            long limit = scheduler.now(unit) - maxAge;

            TimedNode<T> h = head;

            for (;;) {
                if (size <= 1) {
                    head = h;
                    break;
                }
                TimedNode<T> next = h.get();
                if (next == null) {
                    head = h;
                    break;
                }

                if (next.time > limit) {
                    head = h;
                    break;
                }

                h = next;
                size--;
            }

        }

        @Override
        public void add(T value) {
            TimedNode<T> n = new TimedNode<T>(value, scheduler.now(unit));
            TimedNode<T> t = tail;

            tail = n;
            size++;
            t.set(n); // releases both the tail and size

            trim();
        }

        /**
         * Replace a non-empty head node with an empty one to
         * allow the GC of the inaccessible old value.
         */
        @Override
        public void trimHead() {
            TimedNode<T> h = head;
            if (h.value != null) {
                TimedNode<T> n = new TimedNode<T>(null, 0);
                n.lazySet(h.get());
                head = n;
            }
        }

        @Override
        @Nullable
        @SuppressWarnings("unchecked")
        public T getValue() {
            TimedNode<T> h = head;

            for (;;) {
                TimedNode<T> next = h.get();
                if (next == null) {
                    break;
                }
                h = next;
            }

            long limit = scheduler.now(unit) - maxAge;
            if (h.time < limit) {
                return null;
            }

            return h.value;
        }

        TimedNode<T> getHead() {
            TimedNode<T> index = head;
            // skip old entries
            long limit = scheduler.now(unit) - maxAge;
            TimedNode<T> next = index.get();
            while (next != null) {
                long ts = next.time;
                if (ts > limit) {
                    break;
                }
                index = next;
                next = index.get();
            }
            return index;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T[] getValues(T[] array) {
            TimedNode<T> h = getHead();
            int s = size(h);

            if (s == 0) {
                if (array.length != 0) {
                    array[0] = null;
                }
            } else {
                if (array.length < s) {
                    array = (T[]) Array.newInstance(array.getClass().getComponentType(), s);
                }

                int i = 0;
                while (i != s) {
                    TimedNode<T> next = h.get();
                    array[i] = next.value;
                    i++;
                    h = next;
                }
                if (array.length > s) {
                    array[s] = null;
                }
            }

            return array;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void replay(ReplayDisposable<T> rs) {
            if (rs.getAndIncrement() != 0) {
                return;
            }

            int missed = 1;
            final Observer<? super T> a = rs.downstream;

            TimedNode<T> index = (TimedNode<T>)rs.index;
            if (index == null) {
                index = getHead();
            }

            for (;;) {

                if (rs.cancelled) {
                    rs.index = null;
                    return;
                }

                for (;;) {
                    if (rs.cancelled) {
                        rs.index = null;
                        return;
                    }

                    TimedNode<T> n = index.get();

                    if (n == null) {
                        break;
                    }

                    T o = n.value;

                    a.onNext(o);

                    index = n;
                }

                if (index.get() != null) {
                    continue;
                }

                rs.index = index;

                missed = rs.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

        @Override
        public int size() {
            return size(getHead());
        }

        int size(TimedNode<T> h) {
            int s = 0;
            while (s != Integer.MAX_VALUE) {
                TimedNode<T> next = h.get();
                if (next == null) {
                    break;
                }
                s++;
                h = next;
            }

            return s;
        }
    }
}
