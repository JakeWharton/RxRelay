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
import java.util.concurrent.TimeUnit;
import rx.Observer;
import rx.Scheduler;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.TestScheduler;

public final class TestRelay<T> extends Relay<T, T> {
  /**
   * Creates and returns a new {@code TestRelay}.
   *
   * @param <T> the value type
   * @param scheduler a {@link TestScheduler} on which to operate this Relay
   * @return the new {@code TestRelay}
   */
  public static <T> TestRelay<T> create(TestScheduler scheduler) {
    final RelaySubscriptionManager<T> state = new RelaySubscriptionManager<T>();

    state.onAdded = new Action1<RelayObserver<T>>() {
      @Override public void call(RelayObserver<T> o) {
        o.emitFirst(state.getLatest());
      }
    };

    return new TestRelay<T>(state, state, scheduler);
  }

  private final RelaySubscriptionManager<T> state;
  private final Scheduler.Worker innerScheduler;

  private TestRelay(OnSubscribe<T> onSubscribe, RelaySubscriptionManager<T> state,
      TestScheduler scheduler) {
    super(onSubscribe);
    this.state = state;
    this.innerScheduler = scheduler.createWorker();
  }

  /** Schedule a call to {@code call} on TestScheduler. */
  @Override public void call(T v) {
    call(v, 0);
  }

  void _call(T v) {
    for (Observer<? super T> o : state.observers()) {
      o.onNext(v);
    }
  }

  /**
   * Schedule a call to {@code call} relative to "now()" +n milliseconds in the future.
   *
   * @param v the item to emit
   * @param delayTime the number of milliseconds in the future relative to "now()" at which to call
   * {@code call}
   */
  public void call(final T v, long delayTime) {
    innerScheduler.schedule(new Action0() {
      @Override public void call() {
        _call(v);
      }
    }, delayTime, TimeUnit.MILLISECONDS);
  }

  @Override public boolean hasObservers() {
    return state.observers().length > 0;
  }
}
