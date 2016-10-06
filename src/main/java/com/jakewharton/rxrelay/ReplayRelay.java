/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jakewharton.rxrelay;

import com.jakewharton.rxrelay.RelaySubscriptionManager.RelayObserver;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import rx.Observer;
import rx.Scheduler;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Timestamped;

/**
 * Relay that buffers all items it observes and replays them to any {@link Observer} that
 * subscribes.
 * Example usage:
 * <p>
 * <pre> {@code
 * ReplayRelay<Object> relay = ReplayRelay.create();
 * relay.call("one");
 * relay.call("two");
 * relay.call("three");
 * // both of the following will get the events from above
 * relay.subscribe(observer1);
 * relay.subscribe(observer2);
 * } </pre>
 *
 * @param <T> the type of items observed and emitted by the Relay
 */
public class ReplayRelay<T> extends Relay<T, T> {
  /**
   * Creates an unbounded replay relay.
   * <p>
   * The internal buffer is backed by an {@link ArrayList} and starts with an initial capacity of
   * 16. Once the
   * number of items reaches this capacity, it will grow as necessary (usually by 50%). However, as
   * the
   * number of items grows, this causes frequent array reallocation and copying, and may hurt
   * performance
   * and latency. This can be avoided with the {@link #create(int)} overload which takes an initial
   * capacity
   * parameter and can be tuned to reduce the array reallocation frequency as needed.
   *
   * @param <T> the type of items observed and emitted by the Relay
   * @return the created relay
   */
  public static <T> ReplayRelay<T> create() {
    return create(16);
  }

  /**
   * Creates an unbounded replay relay with the specified initial buffer capacity.
   * <p>
   * Use this method to avoid excessive array reallocation while the internal buffer grows to
   * accomodate new
   * items. For example, if you know that the buffer will hold 32k items, you can ask the
   * {@code ReplayRelay} to preallocate its internal array with a capacity to hold that many
   * items. Once
   * the items start to arrive, the internal array won't need to grow, creating less garbage and no
   * overhead
   * due to frequent array-copying.
   *
   * @param <T> the type of items observed and emitted by the Relay
   * @param capacity the initial buffer capacity
   * @return the created relay
   */
  public static <T> ReplayRelay<T> create(int capacity) {
    final UnboundedReplayState<T> state = new UnboundedReplayState<T>(capacity);
    RelaySubscriptionManager<T> ssm = new RelaySubscriptionManager<T>();
    ssm.onStart = new Action1<RelayObserver<T>>() {
      @Override public void call(RelayObserver<T> o) {
        // replay history for this observer using the subscribing thread
        int lastIndex = state.replayObserverFromIndex(0, o);

        // now that it is caught up add to observers
        o.index(lastIndex);
      }
    };
    ssm.onAdded = new Action1<RelayObserver<T>>() {
      @Override public void call(RelayObserver<T> o) {
        synchronized (o) {
          if (!o.first || o.emitting) {
            return;
          }
          o.first = false;
          o.emitting = true;
        }
        boolean skipFinal = false;
        try {
          //noinspection UnnecessaryLocalVariable - Avoid re-read from outside this scope
          final UnboundedReplayState<T> localState = state;
          for (; ; ) {
            int idx = o.<Integer>index();
            int sidx = localState.get();
            if (idx != sidx) {
              Integer j = localState.replayObserverFromIndex(idx, o);
              o.index(j);
            }
            synchronized (o) {
              if (sidx == localState.get()) {
                o.emitting = false;
                skipFinal = true;
                break;
              }
            }
          }
        } finally {
          if (!skipFinal) {
            synchronized (o) {
              o.emitting = false;
            }
          }
        }
      }
    };

    return new ReplayRelay<T>(ssm, ssm, state);
  }

  /**
   * Creates an unbounded replay relay with the bounded-implementation for testing purposes.
   * <p>
   * This variant behaves like the regular unbounded {@code ReplayRelay} created via {@link
   * #create()} but
   * uses the structures of the bounded-implementation. This is by no means intended for the
   * replacement of
   * the original, array-backed and unbounded {@code ReplayRelay} due to the additional overhead
   * of the
   * linked-list based internal buffer. The sole purpose is to allow testing and reasoning about the
   * behavior
   * of the bounded implementations without the interference of the eviction policies.
   *
   * @param <T> the type of items observed and emitted by the Relay
   * @return the created relay
   */
    /* public */
  static <T> ReplayRelay<T> createUnbounded() {
    final BoundedState<T> state =
        new BoundedState<T>(new EmptyEvictionPolicy(), UtilityFunctions.identity(),
            UtilityFunctions.identity());
    return createWithState(state, new DefaultOnAdd<T>(state));
  }

  /**
   * Creates a size-bounded replay relay.
   * <p>
   * In this setting, the {@code ReplayRelay} holds at most {@code size} items in its internal
   * buffer and discards the oldest item.
   * <p>
   * If an observer subscribes while the {@code ReplayRelay} is active, it will observe all items
   * in the buffer at that point in time and each item observed afterwards, even if the buffer
   * evicts items due to the size constraint in the mean time. In other words, once an Observer
   * subscribes, it will receive items without gaps in the sequence.
   *
   * @param <T> the type of items observed and emitted by the Relay
   * @param size the maximum number of buffered items
   * @return the created relay
   */
  public static <T> ReplayRelay<T> createWithSize(int size) {
    final BoundedState<T> state =
        new BoundedState<T>(new SizeEvictionPolicy(size), UtilityFunctions.identity(),
            UtilityFunctions.identity());
    return createWithState(state, new DefaultOnAdd<T>(state));
  }

  /**
   * Creates a time-bounded replay relay.
   * <p>
   * In this setting, the {@code ReplayRelay} internally tags each observed item with a timestamp
   * value supplied by the {@link Scheduler} and keeps only those whose age is less than the
   * supplied time value converted to milliseconds. For example, an item arrives at T=0 and the max
   * age is set to 5; at T&gt;=5 this first item is then evicted by any subsequent item or
   * termination event, leaving the buffer empty.
   * <p>
   * If an observer subscribes while the {@code ReplayRelay} is active, it will observe only those
   * items from within the buffer that have an age less than the specified time, and each item
   * observed thereafter, even if the buffer evicts items due to the time constraint in the mean
   * time. In other words, once an observer subscribes, it observes items without gaps in the
   * sequence except for any outdated items at the beginning of the sequence.
   *
   * @param <T> the type of items observed and emitted by the Relay
   * @param time the maximum age of the contained items
   * @param unit the time unit of {@code time}
   * @param scheduler the {@link Scheduler} that provides the current time
   * @return the created relay
   */
  public static <T> ReplayRelay<T> createWithTime(long time, TimeUnit unit,
      final Scheduler scheduler) {
    final BoundedState<T> state =
        new BoundedState<T>(new TimeEvictionPolicy(unit.toMillis(time), scheduler),
            new AddTimestamped(scheduler), new RemoveTimestamped());
    return createWithState(state, new TimedOnAdd<T>(state, scheduler));
  }

  /**
   * Creates a time- and size-bounded replay relay.
   * <p>
   * In this setting, the {@code ReplayRelay} internally tags each received item with a timestamp
   * value supplied by the {@link Scheduler} and holds at most {@code size} items in its internal
   * buffer. It evicts items from the start of the buffer if their age becomes less-than or equal
   * to the supplied age in milliseconds or the buffer reaches its {@code size} limit.
   * <p>
   * If an observer subscribes while the {@code ReplayRelay} is active, it will observe only those
   * items from within the buffer that have age less than the specified time and each subsequent
   * item, even if the buffer evicts items due to the time constraint in the mean time. In other
   * words, once an observer subscribes, it observes items without gaps in the sequence except for
   * the outdated items at the beginning of the sequence.
   *
   * @param <T> the type of items observed and emitted by the Relay
   * @param time the maximum age of the contained items
   * @param unit the time unit of {@code time}
   * @param size the maximum number of buffered items
   * @param scheduler the {@link Scheduler} that provides the current time
   * @return the created relay
   */
  public static <T> ReplayRelay<T> createWithTimeAndSize(long time, TimeUnit unit, int size,
      final Scheduler scheduler) {
    final BoundedState<T> state = new BoundedState<T>(
        new PairEvictionPolicy(new SizeEvictionPolicy(size),
            new TimeEvictionPolicy(unit.toMillis(time), scheduler)), new AddTimestamped(scheduler),
        new RemoveTimestamped());
    return createWithState(state, new TimedOnAdd<T>(state, scheduler));
  }

  /**
   * Creates a bounded replay relay with the given state shared between the relay and the
   * {@link OnSubscribe} functions.
   *
   * @param <T> the type of items observed and emitted by the Relay
   * @param state the shared state
   * @return the created relay
   */
  private static <T> ReplayRelay<T> createWithState(final BoundedState<T> state,
      Action1<RelayObserver<T>> onStart) {
    RelaySubscriptionManager<T> ssm = new RelaySubscriptionManager<T>();
    ssm.onStart = onStart;
    ssm.onAdded = new Action1<RelayObserver<T>>() {
      @Override public void call(RelayObserver<T> o) {
        synchronized (o) {
          if (!o.first || o.emitting) {
            return;
          }
          o.first = false;
          o.emitting = true;
        }
        boolean skipFinal = false;
        try {
          for (; ; ) {
            NodeList.Node<Object> idx = o.index();
            NodeList.Node<Object> sidx = state.tail();
            if (idx != sidx) {
              NodeList.Node<Object> j = state.replayObserverFromIndex(idx, o);
              o.index(j);
            }
            synchronized (o) {
              if (sidx == state.tail()) {
                o.emitting = false;
                skipFinal = true;
                break;
              }
            }
          }
        } finally {
          if (!skipFinal) {
            synchronized (o) {
              o.emitting = false;
            }
          }
        }
      }
    };

    return new ReplayRelay<T>(ssm, ssm, state);
  }

  /** The state storing the history and the references. */
  private final ReplayState<T> state;
  /** The manager of subscribers. */
  private final RelaySubscriptionManager<T> ssm;

  ReplayRelay(OnSubscribe<T> onSubscribe, RelaySubscriptionManager<T> ssm,
      ReplayState<T> state) {
    super(onSubscribe);
    this.ssm = ssm;
    this.state = state;
  }

  @Override public void call(T t) {
    if (ssm.active) {
      state.next(t);
      for (RelayObserver<? super T> o : ssm.observers()) {
        if (caughtUp(o)) {
          o.onNext(t);
        }
      }
    }
  }

  /**
   * @return Returns the number of subscribers.
   */
    /* Support test. */int subscriberCount() {
    return ssm.get().observers.length;
  }

  @Override public boolean hasObservers() {
    return ssm.observers().length > 0;
  }

  private boolean caughtUp(RelayObserver<? super T> o) {
    if (!o.caughtUp) {
      if (state.replayObserver(o)) {
        o.caughtUp = true;
        o.index(null); // once caught up, no need for the index anymore
      }
      return false;
    } else {
      // it was caught up so proceed the "raw route"
      return true;
    }
  }

  // *********************
  // State implementations
  // *********************

  /**
   * The unbounded replay state.
   *
   * @param <T> the input and output type
   */
  private static final class UnboundedReplayState<T> extends AtomicInteger
      implements ReplayState<T> {
    /** The buffer. */
    private final ArrayList<Object> list;

    UnboundedReplayState(int initialCapacity) {
      list = new ArrayList<Object>(initialCapacity);
    }

    @Override public void next(T n) {
      list.add(NotificationLite.next(n));
      getAndIncrement(); // release index
    }

    private void accept(Observer<? super T> o, int idx) {
      NotificationLite.accept(o, list.get(idx));
    }

    @Override public boolean replayObserver(RelayObserver<? super T> observer) {
      synchronized (observer) {
        observer.first = false;
        if (observer.emitting) {
          return false;
        }
      }

      Integer lastEmittedLink = observer.index();
      if (lastEmittedLink != null) {
        int l = replayObserverFromIndex(lastEmittedLink, observer);
        observer.index(l);
        return true;
      } else {
        throw new IllegalStateException("failed to find lastEmittedLink for: " + observer);
      }
    }

    Integer replayObserverFromIndex(Integer idx, RelayObserver<? super T> observer) {
      int i = idx;
      while (i < get()) {
        accept(observer, i);
        i++;
      }

      return i;
    }

    @Override public int size() {
      return get();
    }

    @Override public boolean isEmpty() {
      return size() == 0;
    }

    @Override @SuppressWarnings("unchecked") public T[] toArray(T[] a) {
      int s = size();
      if (s > 0) {
        if (s > a.length) {
          a = (T[]) Array.newInstance(a.getClass().getComponentType(), s);
        }
        for (int i = 0; i < s; i++) {
          a[i] = (T) list.get(i);
        }
        if (a.length > s) {
          a[s] = null;
        }
      } else if (a.length > 0) {
        a[0] = null;
      }
      return a;
    }

    @Override public T latest() {
      int idx = get();
      if (idx > 0) {
        return NotificationLite.getValue(list.get(idx - 1));
      }
      return null;
    }
  }

  /**
   * The bounded replay state.
   *
   * @param <T> the input and output type
   */
  private static final class BoundedState<T> implements ReplayState<T> {
    final NodeList<Object> list;
    final EvictionPolicy evictionPolicy;
    final Func1<Object, Object> enterTransform;
    final Func1<Object, Object> leaveTransform;
    volatile boolean terminated;
    volatile NodeList.Node<Object> tail;

    BoundedState(EvictionPolicy evictionPolicy, Func1<Object, Object> enterTransform,
        Func1<Object, Object> leaveTransform) {
      this.list = new NodeList<Object>();
      this.tail = list.tail;
      this.evictionPolicy = evictionPolicy;
      this.enterTransform = enterTransform;
      this.leaveTransform = leaveTransform;
    }

    @Override public void next(T value) {
      if (!terminated) {
        list.addLast(enterTransform.call(NotificationLite.next(value)));
        evictionPolicy.evict(list);
        tail = list.tail;
      }
    }

    private void accept(Observer<? super T> o, NodeList.Node<Object> node) {
      NotificationLite.accept(o, leaveTransform.call(node.value));
    }

    /**
     * Accept only non-stale nodes.
     *
     * @param o the target observer
     * @param node the node to accept or reject
     * @param now the current time
     */
    private void acceptTest(Observer<? super T> o, NodeList.Node<Object> node, long now) {
      Object v = node.value;
      if (!evictionPolicy.test(v, now)) {
        NotificationLite.accept(o, leaveTransform.call(v));
      }
    }

    NodeList.Node<Object> head() {
      return list.head;
    }

    NodeList.Node<Object> tail() {
      return tail;
    }

    @Override public boolean replayObserver(RelayObserver<? super T> observer) {
      synchronized (observer) {
        observer.first = false;
        if (observer.emitting) {
          return false;
        }
      }

      NodeList.Node<Object> lastEmittedLink = observer.index();
      NodeList.Node<Object> l = replayObserverFromIndex(lastEmittedLink, observer);
      observer.index(l);
      return true;
    }

    NodeList.Node<Object> replayObserverFromIndex(NodeList.Node<Object> l,
        RelayObserver<? super T> observer) {
      while (l != tail()) {
        accept(observer, l.next);
        l = l.next;
      }
      return l;
    }

    NodeList.Node<Object> replayObserverFromIndexTest(NodeList.Node<Object> l,
        RelayObserver<? super T> observer, long now) {
      while (l != tail()) {
        acceptTest(observer, l.next, now);
        l = l.next;
      }
      return l;
    }

    @Override public int size() {
      int size = 0;
      NodeList.Node<Object> next = head().next;
      while (next != null) {
        size++;
        next = next.next;
      }
      return size;
    }

    @Override public boolean isEmpty() {
      NodeList.Node<Object> l = head();
      NodeList.Node<Object> next = l.next;
      return next == null;
    }

    @Override @SuppressWarnings("unchecked") public T[] toArray(T[] a) {
      List<T> list = new ArrayList<T>();
      NodeList.Node<Object> next = head().next;
      while (next != null) {
        Object o = leaveTransform.call(next.value);

        list.add((T) o);
        next = next.next;
      }
      return list.toArray(a);
    }

    @Override public T latest() {
      NodeList.Node<Object> h = head().next;
      if (h == null) {
        return null;
      }
      while (h != tail()) {
        h = h.next;
      }
      return NotificationLite.getValue(leaveTransform.call(h.value));
    }
  }

  // **************
  // API interfaces
  // **************

  /**
   * General API for replay state management.
   *
   * @param <T> the input and output type
   */
  private interface ReplayState<T> {
    /**
     * Replay contents to the given observer.
     *
     * @param observer the receiver of events
     * @return true if the relay has caught up
     */
    boolean replayObserver(RelayObserver<? super T> observer);

    /**
     * Add an OnNext value to the buffer
     *
     * @param value the value to add
     */
    void next(T value);

    /**
     * @return the number of values in the replay buffer.
     */
    int size();

    /**
     * @return true if the replay buffer is empty
     */
    boolean isEmpty();

    /**
     * Copy the current values from the buffer into the array or create a new array if there isn't
     * enough room.
     *
     * @param a the array to fill in
     * @return the array or a new array containing the current values
     */
    T[] toArray(T[] a);

    /**
     * Returns the latest value that has been buffered or null if no such value
     * present.
     *
     * @return the latest value buffered or null if none
     */
    T latest();
  }

  /** Interface to manage eviction checking. */
  private interface EvictionPolicy {
    /**
     * Subscribe-time checking for stale entries.
     *
     * @param value the value to test
     * @param now the current time
     * @return true if the value may be evicted
     */
    boolean test(Object value, long now);

    /**
     * Evict values from the list.
     *
     * @param list the node list
     */
    void evict(NodeList<Object> list);
  }

  // ************************
  // Callback implementations
  // ************************

  /**
   * Remove elements from the beginning of the list if the size exceeds some threshold.
   */
  private static final class SizeEvictionPolicy implements EvictionPolicy {
    private final int maxSize;

    SizeEvictionPolicy(int maxSize) {
      this.maxSize = maxSize;
    }

    @Override public void evict(NodeList<Object> t1) {
      while (t1.size() > maxSize) {
        t1.removeFirst();
      }
    }

    @Override public boolean test(Object value, long now) {
      return false; // size gets never stale
    }
  }

  /**
   * Remove elements from the beginning of the list if the Timestamped value is older than
   * a threshold.
   */
  private static final class TimeEvictionPolicy implements EvictionPolicy {
    private final long maxAgeMillis;
    private final Scheduler scheduler;

    TimeEvictionPolicy(long maxAgeMillis, Scheduler scheduler) {
      this.maxAgeMillis = maxAgeMillis;
      this.scheduler = scheduler;
    }

    @Override public void evict(NodeList<Object> t1) {
      long now = scheduler.now();
      while (!t1.isEmpty()) {
        NodeList.Node<Object> n = t1.head.next;
        if (test(n.value, now)) {
          t1.removeFirst();
        } else {
          break;
        }
      }
    }

    @Override public boolean test(Object value, long now) {
      Timestamped<?> ts = (Timestamped<?>) value;
      return ts.getTimestampMillis() <= now - maxAgeMillis;
    }
  }

  /**
   * Pairs up two eviction policy callbacks.
   */
  private static final class PairEvictionPolicy implements EvictionPolicy {
    private final EvictionPolicy first;
    private final EvictionPolicy second;

    PairEvictionPolicy(EvictionPolicy first, EvictionPolicy second) {
      this.first = first;
      this.second = second;
    }

    @Override public void evict(NodeList<Object> t1) {
      first.evict(t1);
      second.evict(t1);
    }

    @Override public boolean test(Object value, long now) {
      return first.test(value, now) || second.test(value, now);
    }
  }

  /** Maps the values to Timestamped. */
  private static final class AddTimestamped implements Func1<Object, Object> {
    private final Scheduler scheduler;

    AddTimestamped(Scheduler scheduler) {
      this.scheduler = scheduler;
    }

    @Override public Object call(Object t1) {
      return new Timestamped<Object>(scheduler.now(), t1);
    }
  }

  /** Maps timestamped values back to raw objects. */
  static final class RemoveTimestamped implements Func1<Object, Object> {
    @SuppressWarnings("unchecked")
    @Override public Object call(Object t1) {
      return ((Timestamped<Object>) t1).getValue();
    }
  }

  /**
   * Default action of simply replaying the buffer on subscribe.
   *
   * @param <T> the input and output value type
   */
  private static final class DefaultOnAdd<T> implements Action1<RelayObserver<T>> {
    private final BoundedState<T> state;

    DefaultOnAdd(BoundedState<T> state) {
      this.state = state;
    }

    @Override public void call(RelayObserver<T> t1) {
      NodeList.Node<Object> l = state.replayObserverFromIndex(state.head(), t1);
      t1.index(l);
    }
  }

  /**
   * Action of replaying non-stale entries of the buffer on subscribe
   *
   * @param <T> the input and output value
   */
  private static final class TimedOnAdd<T> implements Action1<RelayObserver<T>> {
    private final BoundedState<T> state;
    private final Scheduler scheduler;

    TimedOnAdd(BoundedState<T> state, Scheduler scheduler) {
      this.state = state;
      this.scheduler = scheduler;
    }

    @Override public void call(RelayObserver<T> t1) {
      NodeList.Node<Object> l;
      if (!state.terminated) {
        // ignore stale entries if still active
        l = state.replayObserverFromIndexTest(state.head(), t1, scheduler.now());
      } else {
        // accept all if terminated
        l = state.replayObserverFromIndex(state.head(), t1);
      }
      t1.index(l);
    }
  }

  /**
   * A singly-linked list with volatile next node pointer.
   *
   * @param <T> the value type
   */
  static final class NodeList<T> {
    /**
     * The node containing the value and references to neighbours.
     *
     * @param <T> the value type
     */
    static final class Node<T> {
      /** The managed value. */
      final T value;
      /** The hard reference to the next node. */
      volatile Node<T> next;

      Node(T value) {
        this.value = value;
      }
    }

    /** The head of the list. */
    final Node<T> head = new Node<T>(null);
    /** The tail of the list. */
    Node<T> tail = head;
    /** The number of elements in the list. */
    int size;

    void addLast(T value) {
      Node<T> t = tail;
      Node<T> t2 = new Node<T>(value);
      t.next = t2;
      tail = t2;
      size++;
    }

    T removeFirst() {
      if (head.next == null) {
        throw new IllegalStateException("Empty!");
      }
      Node<T> t = head.next;
      head.next = t.next;
      if (head.next == null) {
        tail = head;
      }
      size--;
      return t.value;
    }

    boolean isEmpty() {
      return size == 0;
    }

    int size() {
      return size;
    }

    void clear() {
      tail = head;
      size = 0;
    }
  }

  /** Empty eviction policy. */
  private static final class EmptyEvictionPolicy implements EvictionPolicy {
    EmptyEvictionPolicy() {
    }

    @Override public boolean test(Object value, long now) {
      return true;
    }

    @Override public void evict(NodeList<Object> list) {
    }
  }

  /**
   * Returns the current number of items available for replay.
   *
   * @return the number of items available
   */
  public int size() {
    return state.size();
  }

  /**
   * @return true if the Relay holds at least one event available for replay
   */
  public boolean hasAnyValue() {
    return !state.isEmpty();
  }

  public boolean hasValue() {
    return hasAnyValue();
  }

  /**
   * Returns a snapshot of the currently buffered events into
   * the provided {@code a} array or creates a new array if it has not enough capacity.
   *
   * @param a the array to fill in
   * @return the array {@code a} if it had enough capacity or a new array containing the available
   * values
   */
  public T[] getValues(T[] a) {
    return state.toArray(a);
  }

  /** An empty array to trigger getValues() to return a new array. */
  private static final Object[] EMPTY_ARRAY = new Object[0];

  /**
   * Returns a snapshot of the currently buffered events.
   * <p>The operation is threadsafe.
   *
   * @return a snapshot of the currently buffered events.
   * @since (If this graduates from being an Experimental class method, replace this parenthetical
   *with the release number)
   */
  @SuppressWarnings("unchecked")
  public Object[] getValues() {
    T[] r = getValues((T[]) EMPTY_ARRAY);
    if (r == EMPTY_ARRAY) {
      return new Object[0]; // don't leak the default empty array.
    }
    return r;
  }

  public T getValue() {
    return state.latest();
  }
}
