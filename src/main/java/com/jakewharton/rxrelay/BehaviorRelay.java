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
import rx.Observer;
import rx.annotations.Beta;
import rx.functions.Action1;

/**
 * Relay that emits the most recent item it has observed and all subsequent observed items to each
 * subscribed {@link Observer}.
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code
 * // observer will receive all events.
 * BehaviorRelay<Object> relay = BehaviorRelay.create("default");
 * relay.subscribe(observer);
 * relay.call("one");
 * relay.call("two");
 * relay.call("three");
 *
 * // observer will receive the "one", "two" and "three" events, but not "zero"
 * BehaviorRelay<Object> relay = BehaviorRelay.create("default");
 * relay.call("zero");
 * relay.call("one");
 * relay.subscribe(observer);
 * relay.call("two");
 * relay.call("three");
 * } </pre>
 *
 * @param <T> the type of item expected to be observed by the Relay
 */
public class BehaviorRelay<T> extends Relay<T, T> {
  /**
   * Creates a {@link BehaviorRelay} without a default item.
   *
   * @param <T> the type of item the Relay will emit
   * @return the constructed {@link BehaviorRelay}
   */
  public static <T> BehaviorRelay<T> create() {
    return create(null, false);
  }

  /**
   * Creates a {@link BehaviorRelay} that emits the last item it observed and all subsequent
   * items to each {@link Observer} that subscribes to it.
   *
   * @param <T> the type of item the Relay will emit
   * @param defaultValue the item that will be emitted first to any {@link Observer} as long as the
   * {@link BehaviorRelay} has not yet observed any items from its source {@code Observable}
   * @return the constructed {@link BehaviorRelay}
   */
  public static <T> BehaviorRelay<T> create(T defaultValue) {
    return create(defaultValue, true);
  }

  private static <T> BehaviorRelay<T> create(T defaultValue, boolean hasDefault) {
    final RelaySubscriptionManager<T> state = new RelaySubscriptionManager<T>();
    if (hasDefault) {
      state.setLatest(NotificationLite.next(defaultValue));
    }
    state.onAdded = new Action1<RelayObserver<T>>() {
      @Override public void call(RelayObserver<T> o) {
        o.emitFirst(state.getLatest());
      }
    };
    return new BehaviorRelay<T>(state, state);
  }

  private final RelaySubscriptionManager<T> state;

  protected BehaviorRelay(OnSubscribe<T> onSubscribe, RelaySubscriptionManager<T> state) {
    super(onSubscribe);
    this.state = state;
  }

  @Override public void call(T v) {
    Object last = state.getLatest();
    if (last == null || state.active) {
      Object n = NotificationLite.next(v);
      for (RelayObserver<T> ro : state.next(n)) {
        ro.emitNext(n);
      }
    }
  }

  /* test support */ int subscriberCount() {
    return state.observers().length;
  }

  @Override public boolean hasObservers() {
    return state.observers().length > 0;
  }

  /**
   * Check if the Relay has a value.
   * <p>Use the {@link #getValue()} method to retrieve such a value.
   * <p>Note that the value retrieved by {@code getValue()} may get outdated.
   *
   * @return true if and only if the relay has some value.
   */
  @Beta public boolean hasValue() {
    Object o = state.getLatest();
    return NotificationLite.isNext(o);
  }

  /**
   * Returns the current value of the Relay if there is such a value.
   * <p>The method can return {@code null} for various reasons. Use {@link #hasValue()} to
   * determine if such {@code null} is a valid value.
   *
   * @return the current value or {@code null} if the Relay doesn't have a value,
   * has terminated or has an actual {@code null} as a valid value.
   */
  @Beta public T getValue() {
    Object o = state.getLatest();
    if (NotificationLite.isNext(o)) {
      return NotificationLite.getValue(o);
    }
    return null;
  }

  /**
   * Returns a snapshot of the currently buffered events into
   * the provided {@code a} array or creates a new array if it has not enough capacity.
   *
   * @param a the array to fill in
   * @return the array {@code a} if it had enough capacity or a new array containing the available
   * values
   */
  @Beta @SuppressWarnings("unchecked") public T[] getValues(T[] a) {
    Object o = state.getLatest();
    if (NotificationLite.isNext(o)) {
      if (a.length == 0) {
        a = (T[]) Array.newInstance(a.getClass().getComponentType(), 1);
      }
      a[0] = NotificationLite.getValue(o);
      if (a.length > 1) {
        a[1] = null;
      }
    } else if (a.length > 0) {
      a[0] = null;
    }
    return a;
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
  @SuppressWarnings("unchecked") @Beta public Object[] getValues() {
    T[] r = getValues((T[]) EMPTY_ARRAY);
    if (r == EMPTY_ARRAY) {
      return new Object[0]; // don't leak the default empty array.
    }
    return r;
  }
}
