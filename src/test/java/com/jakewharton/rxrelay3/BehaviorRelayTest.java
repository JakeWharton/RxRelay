/**
 * Copyright 2016 Netflix, Inc.
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

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.observers.DefaultObserver;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BehaviorRelayTest {

    @Test
    public void testThatSubscriberReceivesDefaultValueAndSubsequentEvents() {
        BehaviorRelay<String> subject = BehaviorRelay.createDefault("default");

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        subject.accept("one");
        subject.accept("two");
        subject.accept("three");

        verify(observer, times(1)).onNext("default");
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, Mockito.never()).onComplete();
    }

    @Test
    public void testThatSubscriberReceivesLatestAndThenSubsequentEvents() {
        BehaviorRelay<String> subject = BehaviorRelay.createDefault("default");

        subject.accept("one");

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        subject.accept("two");
        subject.accept("three");

        verify(observer, Mockito.never()).onNext("default");
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, Mockito.never()).onComplete();
    }

    @Test(timeout = 1000)
    public void testUnsubscriptionCase() {
        BehaviorRelay<String> src = BehaviorRelay.createDefault("null"); // FIXME was plain null which is not allowed

        for (int i = 0; i < 10; i++) {
            final Observer<Object> o = TestHelper.mockObserver();
            InOrder inOrder = inOrder(o);
            String v = "" + i;
            src.accept(v);
            System.out.printf("Turn: %d%n", i);
            src.firstElement()
                .toObservable()
                .flatMap(new Function<String, Observable<String>>() {

                    @Override
                    public Observable<String> apply(String t1) {
                        return Observable.just(t1 + ", " + t1);
                    }
                })
                .subscribe(new DefaultObserver<String>() {
                    @Override
                    public void onNext(String t) {
                        o.onNext(t);
                    }

                    @Override
                    public void onError(Throwable e) {
                        o.onError(e);
                    }

                    @Override
                    public void onComplete() {
                        o.onComplete();
                    }
                });
            inOrder.verify(o).onNext(v + ", " + v);
            inOrder.verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }

    @Test
    public void testTakeOneSubscriber() {
        BehaviorRelay<Integer> source = BehaviorRelay.createDefault(1);
        final Observer<Object> o = TestHelper.mockObserver();

        source.take(1).subscribe(o);

        verify(o).onNext(1);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));

        assertEquals(0, source.subscriberCount());
        assertFalse(source.hasObservers());
    }

    @Test
    @Ignore("OOMs")
    public void testEmissionSubscriptionRace() throws Exception {
        Scheduler s = Schedulers.io();
        Scheduler.Worker worker = Schedulers.io().createWorker();
        try {
            for (int i = 0; i < 50000; i++) {
                if (i % 1000 == 0) {
                    System.out.println(i);
                }
                final BehaviorRelay<Object> rs = BehaviorRelay.create();

                final CountDownLatch finish = new CountDownLatch(1);
                final CountDownLatch start = new CountDownLatch(1);

                worker.schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            start.await();
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                        rs.accept(1);
                    }
                });

                final AtomicReference<Object> o = new AtomicReference<Object>();

                rs.subscribeOn(s).observeOn(Schedulers.io())
                .subscribe(new DefaultObserver<Object>() {

                    @Override
                    public void onComplete() {
                        o.set(-1);
                        finish.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        o.set(e);
                        finish.countDown();
                    }

                    @Override
                    public void onNext(Object t) {
                        o.set(t);
                        finish.countDown();
                    }

                });
                start.countDown();

                if (!finish.await(5, TimeUnit.SECONDS)) {
                    System.out.println(o.get());
                    System.out.println(rs.hasObservers());
                    fail("Timeout @ " + i);
                    break;
                } else {
                    Assert.assertEquals(1, o.get());
                }
            }
        } finally {
            worker.dispose();
        }
    }

    @Test
    public void testCurrentStateMethodsNormalEmptyStart() {
        BehaviorRelay<Object> as = BehaviorRelay.create();

        assertFalse(as.hasValue());
        assertNull(as.getValue());

        as.accept(1);

        assertTrue(as.hasValue());
        assertEquals(1, as.getValue());
    }

    @Test
    public void testCurrentStateMethodsNormalSomeStart() {
        BehaviorRelay<Object> as = BehaviorRelay.createDefault((Object)1);

        assertTrue(as.hasValue());
        assertEquals(1, as.getValue());

        as.accept(2);

        assertTrue(as.hasValue());
        assertEquals(2, as.getValue());
    }

    @Test
    public void onNextNull() {
        final BehaviorRelay<Object> s = BehaviorRelay.create();

        try {
            s.accept(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("value == null", e.getMessage());
        }
    }

    @Test
    public void cancelOnArrival() {
        BehaviorRelay<Object> p = BehaviorRelay.create();

        assertFalse(p.hasObservers());

        p.test(true).assertEmpty();

        assertFalse(p.hasObservers());
    }

    @Test
    public void addRemoveRace() {
        for (int i = 0; i < 500; i++) {
            final BehaviorRelay<Object> p = BehaviorRelay.create();

            final TestObserver<Object> ts = p.test();

            Runnable r1 = new Runnable() {
                @Override
                @SuppressWarnings("CheckReturnValue")
                public void run() {
                    p.test();
                }
            };

            Runnable r2 = new Runnable() {
                @Override
                public void run() {
                    ts.dispose();
                }
            };

            TestHelper.race(r1, r2, Schedulers.single());
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void subscribeOnNextRace() {
        for (int i = 0; i < 500; i++) {
            final BehaviorRelay<Object> p = BehaviorRelay.createDefault((Object)1);

            final TestObserver[] ts = { null };

            Runnable r1 = new Runnable() {
                @Override
                public void run() {
                    ts[0] = p.test();
                }
            };

            Runnable r2 = new Runnable() {
                @Override
                public void run() {
                    p.accept(2);
                }
            };

            TestHelper.race(r1, r2, Schedulers.single());

            if (ts[0].values().size() == 1) {
                ts[0].assertValue(2).assertNoErrors().assertNotComplete();
            } else {
                ts[0].assertValues(1, 2).assertNoErrors().assertNotComplete();
            }
        }
    }

    @Test
    public void innerDisposed() {
        BehaviorRelay.create()
        .subscribe(new Observer<Object>() {
            @Override
            public void onSubscribe(Disposable d) {
                assertFalse(d.isDisposed());

                d.dispose();

                assertTrue(d.isDisposed());
            }

            @Override
            public void onNext(Object value) {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
    }
}
