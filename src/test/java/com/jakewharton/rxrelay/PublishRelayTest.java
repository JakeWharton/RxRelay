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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.mockito.InOrder;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class PublishRelayTest {
  @Test public void testCompleted() {
    PublishRelay<String> relay = PublishRelay.create();

    @SuppressWarnings("unchecked") Observer<String> observer = mock(Observer.class);
    relay.subscribe(observer);

    relay.call("one");
    relay.call("two");

    @SuppressWarnings("unchecked") Observer<String> anotherObserver = mock(Observer.class);
    relay.subscribe(anotherObserver);

    relay.call("three");

    assertCompletedObserver(observer);
    // todo bug?            assertNeverObserver(anotherObserver);
  }

  private void assertCompletedObserver(Observer<String> observer) {
    verify(observer, times(1)).onNext("one");
    verify(observer, times(1)).onNext("two");
    verify(observer, times(1)).onNext("three");
    verify(observer, never()).onError(any(Throwable.class));
    verify(observer, never()).onCompleted();
  }

  @Test public void testSubscribeMidSequence() {
    PublishRelay<String> relay = PublishRelay.create();

    @SuppressWarnings("unchecked") Observer<String> observer = mock(Observer.class);
    relay.subscribe(observer);

    relay.call("one");
    relay.call("two");

    assertObservedUntilTwo(observer);

    @SuppressWarnings("unchecked") Observer<String> anotherObserver = mock(Observer.class);
    relay.subscribe(anotherObserver);

    relay.call("three");

    assertCompletedObserver(observer);
    assertCompletedStartingWithThreeObserver(anotherObserver);
  }

  private void assertCompletedStartingWithThreeObserver(Observer<String> observer) {
    verify(observer, never()).onNext("one");
    verify(observer, never()).onNext("two");
    verify(observer, times(1)).onNext("three");
    verify(observer, never()).onCompleted();
    verify(observer, never()).onError(any(Throwable.class));
  }

  @Test public void testUnsubscribeFirstObserver() {
    PublishRelay<String> relay = PublishRelay.create();

    @SuppressWarnings("unchecked") Observer<String> observer = mock(Observer.class);
    Subscription subscription = relay.subscribe(observer);

    relay.call("one");
    relay.call("two");

    subscription.unsubscribe();
    assertObservedUntilTwo(observer);

    @SuppressWarnings("unchecked") Observer<String> anotherObserver = mock(Observer.class);
    relay.subscribe(anotherObserver);

    relay.call("three");

    assertObservedUntilTwo(observer);
    assertCompletedStartingWithThreeObserver(anotherObserver);
  }

  @SuppressWarnings("Duplicates")
  private void assertObservedUntilTwo(Observer<String> observer) {
    verify(observer, times(1)).onNext("one");
    verify(observer, times(1)).onNext("two");
    verify(observer, never()).onNext("three");
    verify(observer, never()).onError(any(Throwable.class));
    verify(observer, never()).onCompleted();
  }

  @Test public void testNestedSubscribe() {
    final PublishRelay<Integer> s = PublishRelay.create();

    final AtomicInteger countParent = new AtomicInteger();
    final AtomicInteger countChildren = new AtomicInteger();
    final AtomicInteger countTotal = new AtomicInteger();

    final ArrayList<String> list = new ArrayList<String>();

    s.flatMap(new Func1<Integer, Observable<String>>() {

      @Override public Observable<String> call(final Integer v) {
        countParent.incrementAndGet();

        // then subscribe to relay again (it will not receive the previous value)
        return s.map(new Func1<Integer, String>() {

          @Override public String call(Integer v2) {
            countChildren.incrementAndGet();
            return "Parent: " + v + " Child: " + v2;
          }
        });
      }
    }).subscribe(new Action1<String>() {

      @Override public void call(String v) {
        countTotal.incrementAndGet();
        list.add(v);
      }
    });

    for (int i = 0; i < 10; i++) {
      s.call(i);
    }

    // 9+8+7+6+5+4+3+2+1+0 == 45
    assertEquals(45, list.size());
  }

  /**
   * Should be able to unsubscribe all Observers, have it stop emitting, then subscribe new ones and it start emitting again.
   */
  @Test public void testReSubscribe() {
    final PublishRelay<Integer> ps = PublishRelay.create();

    @SuppressWarnings("unchecked") Observer<Integer> o1 = mock(Observer.class);
    Subscription s1 = ps.subscribe(o1);

    // emit
    ps.call(1);

    // validate we got it
    InOrder inOrder1 = inOrder(o1);
    inOrder1.verify(o1, times(1)).onNext(1);
    inOrder1.verifyNoMoreInteractions();

    // unsubscribe
    s1.unsubscribe();

    // emit again but nothing will be there to receive it
    ps.call(2);

    @SuppressWarnings("unchecked") Observer<Integer> o2 = mock(Observer.class);
    Subscription s2 = ps.subscribe(o2);

    // emit
    ps.call(3);

    // validate we got it
    InOrder inOrder2 = inOrder(o2);
    inOrder2.verify(o2, times(1)).onNext(3);
    inOrder2.verifyNoMoreInteractions();

    s2.unsubscribe();
  }

  @Test(timeout = 1000) public void testUnsubscriptionCase() {
    PublishRelay<String> src = PublishRelay.create();

    for (int i = 0; i < 10; i++) {
      @SuppressWarnings("unchecked") final Observer<Object> o = mock(Observer.class);
      InOrder inOrder = inOrder(o);
      String v = "" + i;
      System.out.printf("Turn: %d%n", i);
      src.first().flatMap(new Func1<String, Observable<String>>() {

        @Override public Observable<String> call(String t1) {
          return Observable.just(t1 + ", " + t1);
        }
      }).subscribe(new Observer<String>() {
        @Override public void onNext(String t) {
          o.onNext(t);
        }

        @Override public void onError(Throwable e) {
          o.onError(e);
        }

        @Override public void onCompleted() {
          o.onCompleted();
        }
      });
      src.call(v);

      inOrder.verify(o).onNext(v + ", " + v);
      inOrder.verify(o).onCompleted();
      verify(o, never()).onError(any(Throwable.class));
    }
  }
}
