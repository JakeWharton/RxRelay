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

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.observers.DefaultObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("OOMs")
public class ReplayRelayBoundedConcurrencyTest {


    @Test
    public void testReplaySubjectEmissionSubscriptionRace() throws Exception {
        Scheduler s = Schedulers.io();
        Scheduler.Worker worker = Schedulers.io().createWorker();
        try {
            for (int i = 0; i < 50000; i++) {
                if (i % 1000 == 0) {
                    System.out.println(i);
                }
                final ReplayRelay<Object> rs = ReplayRelay.createWithSize(2);

                final CountDownLatch finish = new CountDownLatch(1);
                final CountDownLatch start = new CountDownLatch(1);

//                int j = i;

                worker.schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            start.await();
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
//                        System.out.println("> " + j);
                        rs.accept(1);
                    }
                });

                final AtomicReference<Object> o = new AtomicReference<Object>();

                rs
//                .doOnSubscribe(v -> System.out.println("!! " + j))
//                .doOnNext(e -> System.out.println(">> " + j))
                .subscribeOn(s)
                .observeOn(Schedulers.io())
//                .doOnNext(e -> System.out.println(">>> " + j))
                .subscribe(new DefaultObserver<Object>() {

                    @Override
                    protected void onStart() {
                        super.onStart();
                    }

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
                    Assert.fail("Timeout @ " + i);
                    break;
                } else {
                    Assert.assertEquals(1, o.get());
                }
            }
        } finally {
            worker.dispose();
        }
    }
}
