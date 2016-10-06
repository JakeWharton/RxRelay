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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Actions;
import rx.subscriptions.Subscriptions;

/**
 * Represents the typical state and OnSubscribe logic for a Relay implementation.
 *
 * @param <T> the source and return value type
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
/* package */ final class RelaySubscriptionManager<T>
    extends AtomicReference<RelaySubscriptionManager.State<T>> implements OnSubscribe<T> {
  /** Stores the latest value for some relays. */
  volatile Object latest;
  /** Indicates that the relay is active (cheaper than checking the state). */
  boolean active = true;
  /** Action called when a new subscriber subscribes but before it is added to the state. */
  Action1<RelayObserver<T>> onStart = Actions.empty();
  /** Action called after the subscriber has been added to the state. */
  Action1<RelayObserver<T>> onAdded = Actions.empty();

  RelaySubscriptionManager() {
    super(State.EMPTY);
  }

  @Override public void call(final Subscriber<? super T> child) {
    RelayObserver<T> bo = new RelayObserver<T>(child);
    addUnsubscriber(child, bo);
    onStart.call(bo);
    if (!child.isUnsubscribed()) {
      add(bo);
      if (child.isUnsubscribed()) {
        remove(bo);
      }
    }
  }

  /** Registers the unsubscribe action for the given subscriber. */
  private void addUnsubscriber(Subscriber<? super T> child, final RelayObserver<T> bo) {
    child.add(Subscriptions.create(new Action0() {
      @Override public void call() {
        remove(bo);
      }
    }));
  }

  /** Set the latest NotificationLite value. */
  void setLatest(Object value) {
    latest = value;
  }

  /** @return Retrieve the latest NotificationLite value */
  Object getLatest() {
    return latest;
  }

  /** @return the array of active subscribers, don't write into the array! */
  RelayObserver<T>[] observers() {
    return get().observers;
  }

  /**
   * Try to atomically add a RelayObserver to the active state.
   *
   * @param o the RelayObserver to add
   */
  private void add(RelayObserver<T> o) {
    do {
      State oldState = get();
      State newState = oldState.add(o);
      if (compareAndSet(oldState, newState)) {
        onAdded.call(o);
        break;
      }
    } while (true);
  }

  /**
   * Atomically remove the specified RelayObserver from the active observers.
   *
   * @param o the RelayObserver to remove
   */
  void remove(RelayObserver<T> o) {
    do {
      State oldState = get();
      State newState = oldState.remove(o);
      if (newState == oldState || compareAndSet(oldState, newState)) {
        return;
      }
    } while (true);
  }

  /**
   * Set a new latest NotificationLite value and return the active observers.
   *
   * @param n the new latest value
   * @return the array of RelayObservers, don't write into the array!
   */
  RelayObserver<T>[] next(Object n) {
    setLatest(n);
    return get().observers;
  }

  /** State-machine representing the termination state and active RelayObservers. */
  static final class State<T> {
    static final State EMPTY = new State(new RelayObserver[0]);

    final RelayObserver[] observers;

    State(RelayObserver[] observers) {
      this.observers = observers;
    }

    State add(RelayObserver o) {
      RelayObserver[] a = observers;
      int n = a.length;
      RelayObserver[] b = new RelayObserver[n + 1];
      System.arraycopy(observers, 0, b, 0, n);
      b[n] = o;
      return new State<T>(b);
    }

    State remove(RelayObserver o) {
      RelayObserver[] a = observers;
      int n = a.length;
      if (n == 1 && a[0] == o) {
        return EMPTY;
      } else if (n == 0) {
        return this;
      }
      RelayObserver[] b = new RelayObserver[n - 1];
      int j = 0;
      for (int i = 0; i < n; i++) {
        RelayObserver ai = a[i];
        if (ai != o) {
          if (j == n - 1) {
            return this;
          }
          b[j++] = ai;
        }
      }
      if (j == 0) {
        return EMPTY;
      }
      if (j < n - 1) {
        RelayObserver[] c = new RelayObserver[j];
        System.arraycopy(b, 0, c, 0, j);
        b = c;
      }
      return new State<T>(b);
    }
  }

  /**
   * Observer wrapping the actual Subscriber and providing various
   * emission facilities.
   *
   * @param <T> the consumed value type of the actual Observer
   */
  static final class RelayObserver<T> implements Observer<T> {
    /** The actual Observer. */
    final Observer<? super T> actual;
    /** Was the emitFirst run? Guarded by this. */
    boolean first = true;
    /** Guarded by this. */
    boolean emitting;
    /** Guarded by this. */
    List<Object> queue;
    /* volatile */ boolean fastPath;
    /** Indicate that the observer has caught up. */
    protected volatile boolean caughtUp;
    /** Indicate where the observer is at replaying. */
    private volatile Object index;

    RelayObserver(Observer<? super T> actual) {
      this.actual = actual;
    }

    @Override public void onNext(T t) {
      actual.onNext(t);
    }

    @Override public void onError(Throwable e) {
      throw new AssertionError(); // Should never be called in normal operation.
    }

    @Override public void onCompleted() {
      throw new AssertionError(); // Should never be called in normal operation.
    }

    /**
     * Emits the given NotificationLite value and
     * prevents the emitFirst to run if not already run.
     *
     * @param n the NotificationLite value
     */
    void emitNext(Object n) {
      if (!fastPath) {
        synchronized (this) {
          first = false;
          if (emitting) {
            if (queue == null) {
              queue = new ArrayList<Object>();
            }
            queue.add(n);
            return;
          }
        }
        fastPath = true;
      }
      NotificationLite.accept(actual, n);
    }

    /**
     * Tries to emit a NotificationLite value as the first
     * value and drains the queue as long as possible.
     *
     * @param n the NotificationLite value
     */
    void emitFirst(Object n) {
      synchronized (this) {
        if (!first || emitting) {
          return;
        }
        first = false;
        emitting = n != null;
      }
      if (n != null) {
        emitLoop(null, n);
      }
    }

    /**
     * Emits the contents of the queue as long as there are values.
     *
     * @param localQueue the initial queue contents
     * @param current the current content to emit
     */
    private void emitLoop(List<Object> localQueue, Object current) {
      boolean once = true;
      boolean skipFinal = false;
      try {
        do {
          if (localQueue != null) {
            for (Object n : localQueue) {
              accept(n);
            }
          }
          if (once) {
            once = false;
            accept(current);
          }
          synchronized (this) {
            localQueue = queue;
            queue = null;
            if (localQueue == null) {
              emitting = false;
              skipFinal = true;
              break;
            }
          }
        } while (true);
      } finally {
        if (!skipFinal) {
          synchronized (this) {
            emitting = false;
          }
        }
      }
    }

    /**
     * Dispatches a NotificationLite value to the actual Observer.
     *
     * @param n the value to dispatch
     */
    private void accept(Object n) {
      if (n != null) {
        NotificationLite.accept(actual, n);
      }
    }

    /**
     * Returns the stored index.
     *
     * @param <I> the index type
     * @return the index value
     */
    <I> I index() {
      return (I) index;
    }

    /**
     * Sets a new index value.
     *
     * @param newIndex the new index value
     */
    void index(Object newIndex) {
      this.index = newIndex;
    }
  }
}
