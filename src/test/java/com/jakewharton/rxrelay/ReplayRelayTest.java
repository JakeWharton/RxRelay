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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.mockito.InOrder;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;
import rx.schedulers.TestScheduler;

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

public final class ReplayRelayTest {
  @SuppressWarnings("unchecked") @Test public void testSimple() {
    ReplayRelay<String> relay = ReplayRelay.create();

    Observer<String> o1 = mock(Observer.class);
    relay.subscribe(o1);

    relay.call("one");
    relay.call("two");
    relay.call("three");

    assertObservedAllThree(o1);

    // assert that subscribing a 2nd time gets the same data
    Observer<String> o2 = mock(Observer.class);
    relay.subscribe(o2);
    assertObservedAllThree(o2);
  }

  private void assertObservedAllThree(Observer<String> observer) {
    InOrder inOrder = inOrder(observer);
    inOrder.verify(observer, times(1)).onNext("one");
    inOrder.verify(observer, times(1)).onNext("two");
    inOrder.verify(observer, times(1)).onNext("three");
    inOrder.verify(observer, never()).onError(any(Throwable.class));
    inOrder.verify(observer, never()).onCompleted();
    inOrder.verifyNoMoreInteractions();
  }

  private void assertObservedUntilTwo(Observer<String> observer) {
    verify(observer, times(1)).onNext("one");
    verify(observer, times(1)).onNext("two");
    verify(observer, never()).onNext("three");
    verify(observer, never()).onError(any(Throwable.class));
    verify(observer, never()).onCompleted();
  }

  @SuppressWarnings("unchecked") @Test public void testSubscribeMidSequence() {
    ReplayRelay<String> relay = ReplayRelay.create();

    Observer<String> observer = mock(Observer.class);
    relay.subscribe(observer);

    relay.call("one");
    relay.call("two");

    assertObservedUntilTwo(observer);

    Observer<String> anotherObserver = mock(Observer.class);
    relay.subscribe(anotherObserver);
    assertObservedUntilTwo(anotherObserver);

    relay.call("three");

    assertObservedAllThree(observer);
    assertObservedAllThree(anotherObserver);
  }

  @SuppressWarnings("unchecked") @Test public void testUnsubscribeFirstObserver() {
    ReplayRelay<String> relay = ReplayRelay.create();

    Observer<String> observer = mock(Observer.class);
    Subscription subscription = relay.subscribe(observer);

    relay.call("one");
    relay.call("two");

    subscription.unsubscribe();
    assertObservedUntilTwo(observer);

    Observer<String> anotherObserver = mock(Observer.class);
    relay.subscribe(anotherObserver);
    assertObservedUntilTwo(anotherObserver);

    relay.call("three");

    assertObservedUntilTwo(observer);
    assertObservedAllThree(anotherObserver);
  }

  //@Test(timeout = 2000) public void testNewSubscriberDoesntBlockExisting()
  //    throws InterruptedException {
  //
  //  final AtomicReference<String> lastValueForObserver1 = new AtomicReference<String>();
  //  Subscriber<String> observer1 = new Subscriber<String>() {
  //    @Override public void onCompleted() {
  //    }
  //
  //    @Override public void onError(Throwable e) {
  //    }
  //
  //    @Override public void onNext(String v) {
  //      System.out.println("observer1: " + v);
  //      lastValueForObserver1.set(v);
  //    }
  //  };
  //
  //  final AtomicReference<String> lastValueForObserver2 = new AtomicReference<String>();
  //  final CountDownLatch oneReceived = new CountDownLatch(1);
  //  final CountDownLatch makeSlow = new CountDownLatch(1);
  //  final CountDownLatch completed = new CountDownLatch(1);
  //  Subscriber<String> observer2 = new Subscriber<String>() {
  //    @Override public void onCompleted() {
  //      completed.countDown();
  //    }
  //
  //    @Override public void onError(Throwable e) {
  //    }
  //
  //    @Override public void onNext(String v) {
  //      System.out.println("observer2: " + v);
  //      if (v.equals("one")) {
  //        oneReceived.countDown();
  //      } else {
  //        try {
  //          makeSlow.await();
  //        } catch (InterruptedException e) {
  //          e.printStackTrace();
  //        }
  //        lastValueForObserver2.set(v);
  //      }
  //    }
  //  };
  //
  //  ReplayRelay<String> relay = ReplayRelay.create();
  //  relay.subscribe(observer1);
  //  relay.call("one");
  //  assertEquals("one", lastValueForObserver1.get());
  //  relay.call("two");
  //  assertEquals("two", lastValueForObserver1.get());
  //
  //  // use subscribeOn to make this async otherwise we deadlock as we are using CountDownLatches
  //  relay.subscribeOn(Schedulers.newThread()).subscribe(observer2);
  //
  //  System.out.println("before waiting for one");
  //
  //  // wait until observer2 starts having replay occur
  //  oneReceived.await();
  //
  //  System.out.println("after waiting for one");
  //
  //  relay.call("three");
  //
  //  System.out.println("sent three");
  //
  //  // if subscription blocked existing subscribers then 'makeSlow' would cause this to not be there yet
  //  assertEquals("three", lastValueForObserver1.get());
  //
  //  System.out.println("about to send onCompleted");
  //
  //  relay.onCompleted();
  //
  //  System.out.println("completed relay");
  //
  //  // release
  //  makeSlow.countDown();
  //
  //  System.out.println("makeSlow released");
  //
  //  completed.await();
  //  // all of them should be emitted with the last being "three"
  //  assertEquals("three", lastValueForObserver2.get());
  //}

  @Test public void testSubscriptionLeak() {
    ReplayRelay<Object> relay = ReplayRelay.create();

    Subscription s = relay.subscribe();

    assertEquals(1, relay.subscriberCount());

    s.unsubscribe();

    assertEquals(0, relay.subscriberCount());
  }

  @Test(timeout = 1000) public void testUnsubscriptionCase() {
    ReplayRelay<String> src = ReplayRelay.create();

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
      }).subscribe(new Observer<String>() {
        @Override public void onNext(String t) {
          System.out.println(t);
          o.onNext(t);
        }

        @Override public void onError(Throwable e) {
          o.onError(e);
        }

        @Override public void onCompleted() {
          o.onCompleted();
        }
      });
      inOrder.verify(o).onNext("0, 0");
      inOrder.verify(o).onCompleted();
      verify(o, never()).onError(any(Throwable.class));
    }
  }

  @Test public void testTerminateOnce() {
    ReplayRelay<Integer> relay = ReplayRelay.create();
    relay.call(1);
    relay.call(2);

    @SuppressWarnings("unchecked") final Observer<Integer> o = mock(Observer.class);

    relay.unsafeSubscribe(new Subscriber<Integer>() {

      @Override public void onNext(Integer t) {
        o.onNext(t);
      }

      @Override public void onError(Throwable e) {
        o.onError(e);
      }

      @Override public void onCompleted() {
        o.onCompleted();
      }
    });

    verify(o).onNext(1);
    verify(o).onNext(2);
    verify(o, never()).onCompleted();
    verify(o, never()).onError(any(Throwable.class));
  }

  @Test public void testNodeListSimpleAddRemove() {
    ReplayRelay.NodeList<Integer> list = new ReplayRelay.NodeList<Integer>();

    assertEquals(0, list.size());

    // add and remove one

    list.addLast(1);

    assertEquals(1, list.size());

    assertEquals((Integer) 1, list.removeFirst());

    assertEquals(0, list.size());

    // add and remove one again

    list.addLast(1);

    assertEquals(1, list.size());

    assertEquals((Integer) 1, list.removeFirst());

    // add and remove two items

    list.addLast(1);
    list.addLast(2);

    assertEquals(2, list.size());

    assertEquals((Integer) 1, list.removeFirst());
    assertEquals((Integer) 2, list.removeFirst());

    assertEquals(0, list.size());
    // clear two items

    list.addLast(1);
    list.addLast(2);

    assertEquals(2, list.size());

    list.clear();

    assertEquals(0, list.size());
  }

  @Test public void testReplay1AfterTermination() {
    ReplayRelay<Integer> relay = ReplayRelay.createWithSize(1);

    relay.call(1);
    relay.call(2);

    for (int i = 0; i < 1; i++) {
      @SuppressWarnings("unchecked") Observer<Integer> o = mock(Observer.class);

      relay.subscribe(o);

      verify(o, never()).onNext(1);
      verify(o).onNext(2);
      verify(o, never()).onCompleted();
      verify(o, never()).onError(any(Throwable.class));
    }
  }

  @Test public void testReplay1Directly() {
    ReplayRelay<Integer> relay = ReplayRelay.createWithSize(1);

    @SuppressWarnings("unchecked") Observer<Integer> o = mock(Observer.class);

    relay.call(1);
    relay.call(2);

    relay.subscribe(o);

    relay.call(3);

    verify(o, never()).onNext(1);
    verify(o).onNext(2);
    verify(o).onNext(3);
    verify(o, never()).onCompleted();
    verify(o, never()).onError(any(Throwable.class));
  }

  @Test public void testReplayTimestampedAfterTermination() {
    TestScheduler scheduler = new TestScheduler();
    ReplayRelay<Integer> source = ReplayRelay.createWithTime(1, TimeUnit.SECONDS, scheduler);

    source.call(1);

    scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

    source.call(2);

    scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

    source.call(3);

    @SuppressWarnings("unchecked") Observer<Integer> o = mock(Observer.class);

    source.subscribe(o);

    verify(o, never()).onNext(1);
    verify(o, never()).onNext(2);
    verify(o).onNext(3);
    verify(o, never()).onCompleted();
    verify(o, never()).onError(any(Throwable.class));
  }

  @Test public void testReplayTimestampedDirectly() {
    TestScheduler scheduler = new TestScheduler();
    ReplayRelay<Integer> source = ReplayRelay.createWithTime(1, TimeUnit.SECONDS, scheduler);

    source.call(1);

    scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

    @SuppressWarnings("unchecked") Observer<Integer> o = mock(Observer.class);

    source.subscribe(o);

    source.call(2);

    scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

    source.call(3);

    scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

    verify(o, never()).onError(any(Throwable.class));
    verify(o, never()).onNext(1);
    verify(o).onNext(2);
    verify(o).onNext(3);
    verify(o, never()).onCompleted();
  }

  @Test public void testSizeAndHasAnyValueUnbounded() {
    ReplayRelay<Object> relay = ReplayRelay.create();

    assertEquals(0, relay.size());
    assertFalse(relay.hasAnyValue());

    relay.call(1);

    assertEquals(1, relay.size());
    assertTrue(relay.hasAnyValue());

    relay.call(1);

    assertEquals(2, relay.size());
    assertTrue(relay.hasAnyValue());
  }

  @Test public void testSizeAndHasAnyValueEffectivelyUnbounded() {
    ReplayRelay<Object> relay = ReplayRelay.createUnbounded();

    assertEquals(0, relay.size());
    assertFalse(relay.hasAnyValue());

    relay.call(1);

    assertEquals(1, relay.size());
    assertTrue(relay.hasAnyValue());

    relay.call(1);

    assertEquals(2, relay.size());
    assertTrue(relay.hasAnyValue());
  }

  @Test public void testSizeAndHasAnyValueSizeBounded() {
    ReplayRelay<Object> relay = ReplayRelay.createWithSize(1);

    assertEquals(0, relay.size());
    assertFalse(relay.hasAnyValue());

    for (int i = 0; i < 1000; i++) {
      relay.call(i);

      assertEquals(1, relay.size());
      assertTrue(relay.hasAnyValue());
    }

    assertEquals(1, relay.size());
    assertTrue(relay.hasAnyValue());
  }

  @Test public void testSizeAndHasAnyValueTimeBounded() {
    TestScheduler ts = new TestScheduler();
    ReplayRelay<Object> rr = ReplayRelay.createWithTime(1, TimeUnit.SECONDS, ts);

    assertEquals(0, rr.size());
    assertFalse(rr.hasAnyValue());

    for (int i = 0; i < 1000; i++) {
      rr.call(i);
      ts.advanceTimeBy(2, TimeUnit.SECONDS);
      assertEquals(1, rr.size());
      assertTrue(rr.hasAnyValue());
    }
  }

  @Test public void testGetValues() {
    ReplayRelay<Object> relay = ReplayRelay.create();
    Object[] expected = new Object[10];
    for (int i = 0; i < expected.length; i++) {
      expected[i] = i;
      relay.call(i);
      assertArrayEquals(Arrays.copyOf(expected, i + 1), relay.getValues());
    }

    assertArrayEquals(expected, relay.getValues());
  }

  @Test public void testGetValuesUnbounded() {
    ReplayRelay<Object> relay = ReplayRelay.createUnbounded();
    Object[] expected = new Object[10];
    for (int i = 0; i < expected.length; i++) {
      expected[i] = i;
      relay.call(i);
      assertArrayEquals(Arrays.copyOf(expected, i + 1), relay.getValues());
    }

    assertArrayEquals(expected, relay.getValues());
  }


  @Test public void testReplayRelayValueRelayIncomplete() {
    ReplayRelay<Integer> relay = ReplayRelay.create();
    relay.call(1);

    assertFalse(relay.hasObservers());
    assertEquals((Integer) 1, relay.getValue());
    assertTrue(relay.hasValue());
    assertArrayEquals(new Object[] { 1 }, relay.getValues());
    assertArrayEquals(new Integer[] { 1 }, relay.getValues(new Integer[0]));
    assertArrayEquals(new Integer[] { 1 }, relay.getValues(new Integer[] { 0 }));
    assertArrayEquals(new Integer[] { 1, null }, relay.getValues(new Integer[] { 0, 0 }));
  }


  @Test public void testReplayRelayValueRelayBoundedIncomplete() {
    ReplayRelay<Integer> async = ReplayRelay.createWithSize(1);
    async.call(0);
    async.call(1);

    assertFalse(async.hasObservers());
    assertEquals((Integer) 1, async.getValue());
    assertTrue(async.hasValue());
    assertArrayEquals(new Object[] { 1 }, async.getValues());
    assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[0]));
    assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[] { 0 }));
    assertArrayEquals(new Integer[] { 1, null }, async.getValues(new Integer[] { 0, 0 }));
  }

  @Test public void testReplayRelayValueRelayBoundedEmptyIncomplete() {
    ReplayRelay<Integer> async = ReplayRelay.createWithSize(1);

    assertFalse(async.hasObservers());
    assertNull(async.getValue());
    assertFalse(async.hasValue());
    assertArrayEquals(new Object[] {}, async.getValues());
    assertArrayEquals(new Integer[] {}, async.getValues(new Integer[0]));
    assertArrayEquals(new Integer[] { null }, async.getValues(new Integer[] { 0 }));
    assertArrayEquals(new Integer[] { null, 0 }, async.getValues(new Integer[] { 0, 0 }));
  }

  @Test public void testReplayRelayValueRelayEmptyIncomplete() {
    ReplayRelay<Integer> async = ReplayRelay.create();

    assertFalse(async.hasObservers());
    assertNull(async.getValue());
    assertFalse(async.hasValue());
    assertArrayEquals(new Object[] {}, async.getValues());
    assertArrayEquals(new Integer[] {}, async.getValues(new Integer[0]));
    assertArrayEquals(new Integer[] { null }, async.getValues(new Integer[] { 0 }));
    assertArrayEquals(new Integer[] { null, 0 }, async.getValues(new Integer[] { 0, 0 }));
  }
}
