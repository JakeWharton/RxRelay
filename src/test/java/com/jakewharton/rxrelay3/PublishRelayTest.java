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
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.observers.DefaultObserver;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PublishRelayTest {

    @Test
    @SuppressWarnings("CheckReturnValue")
    public void testNestedSubscribe() {
        final PublishRelay<Integer> s = PublishRelay.create();

        final AtomicInteger countParent = new AtomicInteger();
        final AtomicInteger countChildren = new AtomicInteger();
        final AtomicInteger countTotal = new AtomicInteger();

        final ArrayList<String> list = new ArrayList<String>();

        s.flatMap(new Function<Integer, Observable<String>>() {

            @Override
            public Observable<String> apply(final Integer v) {
                countParent.incrementAndGet();

                // then subscribe to subject again (it will not receive the previous value)
                return s.map(new Function<Integer, String>() {

                    @Override
                    public String apply(Integer v2) {
                        countChildren.incrementAndGet();
                        return "Parent: " + v + " Child: " + v2;
                    }

                });
            }

        }).subscribe(new Consumer<String>() {

            @Override
            public void accept(String v) {
                countTotal.incrementAndGet();
                list.add(v);
            }

        });

        for (int i = 0; i < 10; i++) {
            s.accept(i);
        }

        //            System.out.println("countParent: " + countParent.get());
        //            System.out.println("countChildren: " + countChildren.get());
        //            System.out.println("countTotal: " + countTotal.get());

        // 9+8+7+6+5+4+3+2+1+0 == 45
        assertEquals(45, list.size());
    }

    /**
     * Should be able to unsubscribe all Subscribers, have it stop emitting, then subscribe new ones and it start emitting again.
     */
    @Test
    public void testReSubscribe() {
        final PublishRelay<Integer> ps = PublishRelay.create();

        Observer<Integer> o1 = TestHelper.mockObserver();
        TestObserver<Integer> ts = new TestObserver<Integer>(o1);
        ps.subscribe(ts);

        // emit
        ps.accept(1);

        // validate we got it
        InOrder inOrder1 = inOrder(o1);
        inOrder1.verify(o1, times(1)).onNext(1);
        inOrder1.verifyNoMoreInteractions();

        // unsubscribe
        ts.dispose();

        // emit again but nothing will be there to receive it
        ps.accept(2);

        Observer<Integer> o2 = TestHelper.mockObserver();
        TestObserver<Integer> ts2 = new TestObserver<Integer>(o2);
        ps.subscribe(ts2);

        // emit
        ps.accept(3);

        // validate we got it
        InOrder inOrder2 = inOrder(o2);
        inOrder2.verify(o2, times(1)).onNext(3);
        inOrder2.verifyNoMoreInteractions();

        ts2.dispose();
    }

    @Test(timeout = 1000)
    public void testUnsubscriptionCase() {
        PublishRelay<String> src = PublishRelay.create();

        for (int i = 0; i < 10; i++) {
            final Observer<Object> o = TestHelper.mockObserver();
            InOrder inOrder = inOrder(o);
            String v = "" + i;
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
            src.accept(v);

            inOrder.verify(o).onNext(v + ", " + v);
            inOrder.verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }

    @Test
    public void crossCancel() {
        final TestObserver<Integer> ts1 = new TestObserver<Integer>();
        TestObserver<Integer> ts2 = new TestObserver<Integer>() {
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
                ts1.dispose();
            }
        };

        PublishRelay<Integer> pp = PublishRelay.create();

        pp.subscribe(ts2);
        pp.subscribe(ts1);

        pp.accept(1);

        ts2.assertValue(1);

        ts1.assertNoValues();
    }

    @Test
    public void onSubscribeCancelsImmediately() {
        PublishRelay<Integer> pp = PublishRelay.create();

        TestObserver<Integer> ts = pp.test();

        pp.subscribe(new Observer<Integer>() {

            @Override
            public void onSubscribe(Disposable s) {
                s.dispose();
                s.dispose();
            }

            @Override
            public void onNext(Integer t) {

            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }

        });

        ts.dispose();

        assertFalse(pp.hasObservers());
    }

    @Test
    public void addRemoveRance() throws Exception {

        for (int i = 0; i < 100; i++) {
            final PublishRelay<Integer> pp = PublishRelay.create();

            final TestObserver<Integer> ts = pp.test();

            Runnable r1 = new Runnable() {
                @Override
                public void run() {
                    pp.subscribe();
                }
            };
            Runnable r2 = new Runnable() {
                @Override
                public void run() {
                    ts.dispose();
                }
            };

            TestHelper.race(r1, r2, Schedulers.io());
        }
    }

    @Test
    public void nullOnNext() {
        PublishRelay<Integer> pp = PublishRelay.create();

        TestObserver<Integer> ts = pp.test();

        assertTrue(pp.hasObservers());

        try {
            pp.accept(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("value == null", e.getMessage());
        }
    }

    @Test
    @SuppressWarnings("CheckReturnValue")
    public void subscribedTo() {
        PublishRelay<Integer> pp = PublishRelay.create();
        PublishRelay<Integer> pp2 = PublishRelay.create();

        pp.subscribe(pp2);

        TestObserver<Integer> ts = pp2.test();

        pp.accept(1);
        pp.accept(2);

        ts.assertValues(1, 2);
    }
}
