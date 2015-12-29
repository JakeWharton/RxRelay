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

import org.junit.Test;
import org.mockito.InOrder;
import rx.Observable;
import rx.Observer;
import rx.functions.Func1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public final class BehaviorRelayTest {
  @Test public void testThatObserverReceivesDefaultValueAndSubsequentEvents() {
    BehaviorRelay<String> relay = BehaviorRelay.create("default");

    @SuppressWarnings("unchecked") Observer<String> observer = mock(Observer.class);
    relay.subscribe(observer);

    relay.call("one");
    relay.call("two");
    relay.call("three");

    verify(observer, times(1)).onNext("default");
    verify(observer, times(1)).onNext("one");
    verify(observer, times(1)).onNext("two");
    verify(observer, times(1)).onNext("three");
    verifyNoMoreInteractions(observer);
  }

  @Test public void testThatObserverReceivesLatestAndThenSubsequentEvents() {
    BehaviorRelay<String> relay = BehaviorRelay.create("default");

    relay.call("one");

    @SuppressWarnings("unchecked") Observer<String> observer = mock(Observer.class);
    relay.subscribe(observer);

    relay.call("two");
    relay.call("three");

    verify(observer, times(1)).onNext("one");
    verify(observer, times(1)).onNext("two");
    verify(observer, times(1)).onNext("three");
    verifyNoMoreInteractions(observer);
  }

  @Test(timeout = 1000) public void testUnsubscriptionCase() {
    BehaviorRelay<String> src = BehaviorRelay.create((String) null);

    for (int i = 0; i < 10; i++) {
      @SuppressWarnings("unchecked") final Observer<Object> o = mock(Observer.class);
      InOrder inOrder = inOrder(o);
      String v = "" + i;
      src.call(v);
      System.out.printf("Turn: %d%n", i);
      src.first().flatMap(new Func1<String, Observable<String>>() {

        @Override public Observable<String> call(String t1) {
          return Observable.just(t1 + ", " + t1);
        }
      }).subscribe(o);

      inOrder.verify(o).onNext(v + ", " + v);
      inOrder.verify(o).onCompleted();
      verify(o, never()).onError(any(Throwable.class));
    }
  }

  @Test public void testStartEmpty() {
    BehaviorRelay<Integer> source = BehaviorRelay.create();
    @SuppressWarnings("unchecked") final Observer<Object> o = mock(Observer.class);
    InOrder inOrder = inOrder(o);

    source.subscribe(o);

    inOrder.verify(o, never()).onNext(any());
    inOrder.verify(o, never()).onCompleted();

    source.call(1);

    verify(o, never()).onError(any(Throwable.class));
    verify(o, never()).onCompleted();

    inOrder.verify(o).onNext(1);
    inOrder.verifyNoMoreInteractions();
  }

  @Test public void testStartEmptyThenAddOne() {
    BehaviorRelay<Integer> source = BehaviorRelay.create();
    @SuppressWarnings("unchecked") final Observer<Object> o = mock(Observer.class);
    InOrder inOrder = inOrder(o);

    source.call(1);

    source.subscribe(o);

    inOrder.verify(o).onNext(1);

    source.call(2);

    inOrder.verify(o).onNext(2);

    verify(o, never()).onCompleted();
    verify(o, never()).onError(any(Throwable.class));
    inOrder.verifyNoMoreInteractions();
  }

  @Test public void testTakeOneSubscriber() {
    BehaviorRelay<Integer> source = BehaviorRelay.create(1);
    @SuppressWarnings("unchecked") final Observer<Object> o = mock(Observer.class);

    source.take(1).subscribe(o);

    verify(o).onNext(1);
    verify(o).onCompleted();
    verifyNoMoreInteractions(o);

    assertEquals(0, source.subscriberCount());
    assertFalse(source.hasObservers());
  }

  @Test public void testBehaviorRelayValueRelayIncomplete() {
    BehaviorRelay<Integer> async = BehaviorRelay.create();
    async.call(1);

    assertFalse(async.hasObservers());
    assertEquals((Integer) 1, async.getValue());
    assertTrue(async.hasValue());
    assertArrayEquals(new Object[] { 1 }, async.getValues());
    assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[0]));
    assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[] { 0 }));
    assertArrayEquals(new Integer[] { 1, null }, async.getValues(new Integer[] { 0, 0 }));
  }

  @Test public void testBehaviorRelayIncompleteEmpty() {
    BehaviorRelay<Integer> async = BehaviorRelay.create();

    assertFalse(async.hasObservers());
    assertNull(async.getValue());
    assertFalse(async.hasValue());
    assertArrayEquals(new Object[] {}, async.getValues());
    assertArrayEquals(new Integer[] {}, async.getValues(new Integer[0]));
    assertArrayEquals(new Integer[] { null }, async.getValues(new Integer[] { 0 }));
    assertArrayEquals(new Integer[] { null, 0 }, async.getValues(new Integer[] { 0, 0 }));
  }
}
