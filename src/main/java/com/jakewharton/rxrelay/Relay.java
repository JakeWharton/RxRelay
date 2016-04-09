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

import rx.Observable;
import rx.Observer;
import rx.functions.Action1;

/** Represents an object that is both an Observable and an Action1. */
public abstract class Relay<T, R> extends Observable<R> implements Action1<T> {
  protected Relay(OnSubscribe<R> onSubscribe) {
    super(onSubscribe);
  }

  /**
   * Portrays an object of a Relay implementation as a simple Action1 object. This is useful, for instance,
   * when you have an implementation of a Relay but you want to hide the Observable properties and
   * methods of this subclass from whomever you are passing the Relay to.
   *
   * @return an Action1 that hides the Observable identity of this Relay.
   */
  public Action1<T> asAction() {
    return new Action1<T>() {
      @Override
      public void call(T t) {
        Relay.this.call(t);
      }
    };
  }

  /**
   * Indicates whether the {@link Relay} has {@link Observer Observers} subscribed to it.
   *
   * @return true if there is at least one Observer subscribed to this Relay, false otherwise
   */
  public abstract boolean hasObservers();

  /**
   * Wraps a {@link Relay} so that it is safe to call {@link #call} from different threads.
   * <p>
   * When you use an ordinary {@link Relay} as a {@link Action1}, you must take care not to call
   * its {@link Action1#call} method from multiple threads, as this could lead to non-serialized
   * calls, which violates
   * <a href="http://reactivex.io/documentation/contract.html">the Observable contract</a> and
   * creates an ambiguity in the resulting Relay.
   * <p>
   * To protect a {@code Relay} from this danger, you can convert it into a {@link SerializedRelay}
   * with code like the following:
   * <p><pre>{@code
   * safeRelay = unsafeRelay.toSerialized();
   * }</pre>
   *
   * @return SerializedRelay wrapping the current Relay
   */
  public final SerializedRelay<T, R> toSerialized() {
    if (getClass() == SerializedRelay.class) {
      return (SerializedRelay<T, R>) this;
    }
    return new SerializedRelay<T, R>(this);
  }
}
