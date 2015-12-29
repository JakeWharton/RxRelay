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

import java.util.concurrent.TimeUnit;
import org.junit.Test;
import rx.Observer;
import rx.schedulers.TestScheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class TestRelayTest {
  @Test public void testObserverPropagateValueAfterTriggeringActions() {
    TestScheduler scheduler = new TestScheduler();

    TestRelay<Integer> relay = TestRelay.create(scheduler);
    @SuppressWarnings("unchecked") Observer<Integer> observer = mock(Observer.class);
    relay.subscribe(observer);

    relay.call(1);
    scheduler.triggerActions();

    verify(observer, times(1)).onNext(1);
  }

  @Test public void testObserverPropagateValueInFutureTimeAfterTriggeringActions() {
    TestScheduler scheduler = new TestScheduler();
    scheduler.advanceTimeTo(100, TimeUnit.SECONDS);

    TestRelay<Integer> relay = TestRelay.create(scheduler);
    @SuppressWarnings("unchecked") Observer<Integer> observer = mock(Observer.class);
    relay.subscribe(observer);

    relay.call(1);
    scheduler.triggerActions();

    verify(observer, times(1)).onNext(1);
  }
}
