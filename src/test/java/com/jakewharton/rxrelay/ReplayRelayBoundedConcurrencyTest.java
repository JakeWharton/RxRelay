///**
// * Copyright 2014 Netflix, Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.jakewharton.rxrelay;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.BrokenBarrierException;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.CyclicBarrier;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicReference;
//import org.junit.Assert;
//import org.junit.Test;
//import rx.Observable;
//import rx.Observable.OnSubscribe;
//import rx.Observer;
//import rx.Scheduler;
//import rx.Subscriber;
//import rx.functions.Action0;
//import rx.functions.Action1;
//import rx.observers.TestSubscriber;
//import rx.schedulers.Schedulers;
//
//import static org.junit.Assert.assertEquals;
//
//public class ReplayRelayBoundedConcurrencyTest {
//
//  public static void main(String args[]) {
//    try {
//      for (int i = 0; i < 100; i++) {
//        new ReplayRelayConcurrencyTest().testReplayRelayConcurrentSubscriptions();
//        new ReplayRelayConcurrencyTest().testReplayRelayConcurrentSubscribersDoingReplayDontBlockEachOther();
//      }
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//    }
//  }
//
//  @Test(timeout = 4000)
//  public void testReplayRelayConcurrentSubscribersDoingReplayDontBlockEachOther()
//      throws InterruptedException {
//    final ReplayRelay<Long> replay = ReplayRelay.createUnbounded();
//    Thread source = new Thread(new Runnable() {
//
//      @Override public void run() {
//        Observable.create(new OnSubscribe<Long>() {
//
//          @Override public void call(Subscriber<? super Long> o) {
//            System.out.println("********* Start Source Data ***********");
//            for (long l = 1; l <= 10000; l++) {
//              o.onNext(l);
//            }
//            System.out.println("********* Finished Source Data ***********");
//            o.onCompleted();
//          }
//        }).subscribe(replay);
//      }
//    });
//    source.start();
//
//    long v = replay.toBlocking().last();
//    assertEquals(10000, v);
//
//    // it's been played through once so now it will all be replays
//    final CountDownLatch slowLatch = new CountDownLatch(1);
//    Thread slowThread = new Thread(new Runnable() {
//
//      @Override public void run() {
//        Subscriber<Long> slow = new Subscriber<Long>() {
//
//          @Override public void onCompleted() {
//            System.out.println("*** Slow Observer completed");
//            slowLatch.countDown();
//          }
//
//          @Override public void onError(Throwable e) {
//          }
//
//          @Override public void onNext(Long args) {
//            if (args == 1) {
//              System.out.println("*** Slow Observer STARTED");
//            }
//            try {
//              if (args % 10 == 0) {
//                Thread.sleep(1);
//              }
//            } catch (InterruptedException e) {
//              e.printStackTrace();
//            }
//          }
//        };
//        replay.subscribe(slow);
//        try {
//          slowLatch.await();
//        } catch (InterruptedException e1) {
//          e1.printStackTrace();
//        }
//      }
//    });
//    slowThread.start();
//
//    Thread fastThread = new Thread(new Runnable() {
//
//      @Override public void run() {
//        final CountDownLatch fastLatch = new CountDownLatch(1);
//        Subscriber<Long> fast = new Subscriber<Long>() {
//
//          @Override public void onCompleted() {
//            System.out.println("*** Fast Observer completed");
//            fastLatch.countDown();
//          }
//
//          @Override public void onError(Throwable e) {
//          }
//
//          @Override public void onNext(Long args) {
//            if (args == 1) {
//              System.out.println("*** Fast Observer STARTED");
//            }
//          }
//        };
//        replay.subscribe(fast);
//        try {
//          fastLatch.await();
//        } catch (InterruptedException e1) {
//          e1.printStackTrace();
//        }
//      }
//    });
//    fastThread.start();
//    fastThread.join();
//
//    // slow should not yet be completed when fast completes
//    assertEquals(1, slowLatch.getCount());
//
//    slowThread.join();
//  }
//
//  @Test public void testReplayRelayConcurrentSubscriptions() throws InterruptedException {
//    final ReplayRelay<Long> replay = ReplayRelay.createUnbounded();
//    Thread source = new Thread(new Runnable() {
//
//      @Override public void run() {
//        Observable.create(new OnSubscribe<Long>() {
//
//          @Override public void call(Subscriber<? super Long> o) {
//            System.out.println("********* Start Source Data ***********");
//            for (long l = 1; l <= 10000; l++) {
//              o.onNext(l);
//            }
//            System.out.println("********* Finished Source Data ***********");
//            o.onCompleted();
//          }
//        }).subscribe(replay);
//      }
//    });
//
//    // used to collect results of each thread
//    final List<List<Long>> listOfListsOfValues =
//        Collections.synchronizedList(new ArrayList<List<Long>>());
//    final List<Thread> threads = Collections.synchronizedList(new ArrayList<Thread>());
//
//    for (int i = 1; i <= 200; i++) {
//      final int count = i;
//      if (count == 20) {
//        // start source data after we have some already subscribed
//        // and while others are in process of subscribing
//        source.start();
//      }
//      if (count == 100) {
//        // wait for source to finish then keep adding after it's done
//        source.join();
//      }
//      Thread t = new Thread(new Runnable() {
//
//        @Override public void run() {
//          List<Long> values = replay.toList().toBlocking().last();
//          listOfListsOfValues.add(values);
//          System.out.println("Finished thread: " + count);
//        }
//      });
//      t.start();
//      System.out.println("Started thread: " + i);
//      threads.add(t);
//    }
//
//    // wait for all threads to complete
//    for (Thread t : threads) {
//      t.join();
//    }
//
//    // assert all threads got the same results
//    List<Long> sums = new ArrayList<Long>();
//    for (List<Long> values : listOfListsOfValues) {
//      long v = 0;
//      for (long l : values) {
//        v += l;
//      }
//      sums.add(v);
//    }
//
//    long expected = sums.get(0);
//    boolean success = true;
//    for (long l : sums) {
//      if (l != expected) {
//        success = false;
//        System.out.println("FAILURE => Expected " + expected + " but got: " + l);
//      }
//    }
//
//    if (success) {
//      System.out.println("Success! " + sums.size() + " each had the same sum of " + expected);
//    } else {
//      throw new RuntimeException("Concurrency Bug");
//    }
//  }
//
//  @Test public void testReplayRelayEmissionSubscriptionRace() throws Exception {
//    Scheduler s = Schedulers.io();
//    Scheduler.Worker worker = Schedulers.io().createWorker();
//    try {
//      for (int i = 0; i < 50000; i++) {
//        if (i % 1000 == 0) {
//          System.out.println(i);
//        }
//        final ReplayRelay<Object> rs = ReplayRelay.createWithSize(2);
//
//        final CountDownLatch finish = new CountDownLatch(1);
//        final CountDownLatch start = new CountDownLatch(1);
//
//        worker.schedule(new Action0() {
//          @Override public void call() {
//            try {
//              start.await();
//            } catch (Exception e1) {
//              e1.printStackTrace();
//            }
//            rs.call(1);
//          }
//        });
//
//        final AtomicReference<Object> o = new AtomicReference<Object>();
//
//        rs.subscribeOn(s).observeOn(Schedulers.io()).subscribe(new Observer<Object>() {
//
//          @Override public void onCompleted() {
//            o.set(-1);
//            finish.countDown();
//          }
//
//          @Override public void onError(Throwable e) {
//            o.set(e);
//            finish.countDown();
//          }
//
//          @Override public void onNext(Object t) {
//            o.set(t);
//            finish.countDown();
//          }
//        });
//        start.countDown();
//
//        if (!finish.await(5, TimeUnit.SECONDS)) {
//          System.out.println(o.get());
//          System.out.println(rs.hasObservers());
//          Assert.fail("Timeout @ " + i);
//          break;
//        } else {
//          Assert.assertEquals(1, o.get());
//        }
//      }
//    } finally {
//      worker.unsubscribe();
//    }
//  }
//
//  @Test(timeout = 5000) public void testConcurrentSizeAndHasAnyValue() throws InterruptedException {
//    final ReplayRelay<Object> rs = ReplayRelay.createUnbounded();
//    final CyclicBarrier cb = new CyclicBarrier(2);
//
//    Thread t = new Thread(new Runnable() {
//      @Override public void run() {
//        try {
//          cb.await();
//        } catch (InterruptedException e) {
//          return;
//        } catch (BrokenBarrierException e) {
//          return;
//        }
//        for (int i = 0; i < 1000000; i++) {
//          rs.call(i);
//        }
//        System.out.println("Replay fill Thread finished!");
//      }
//    });
//    t.start();
//    try {
//      cb.await();
//    } catch (InterruptedException e) {
//      return;
//    } catch (BrokenBarrierException e) {
//      return;
//    }
//    int lastSize = 0;
//    for (; !rs.hasThrowable() && !rs.hasCompleted(); ) {
//      int size = rs.size();
//      boolean hasAny = rs.hasAnyValue();
//      Object[] values = rs.getValues();
//      if (size < lastSize) {
//        Assert.fail("Size decreased! " + lastSize + " -> " + size);
//      }
//      if ((size > 0) && !hasAny) {
//        Assert.fail("hasAnyValue reports emptyness but size doesn't");
//      }
//      if (size > values.length) {
//        Assert.fail("Got fewer values than size! " + size + " -> " + values.length);
//      }
//      for (int i = 0; i < values.length - 1; i++) {
//        Integer v1 = (Integer) values[i];
//        Integer v2 = (Integer) values[i + 1];
//        assertEquals(1, v2 - v1);
//      }
//      lastSize = size;
//    }
//
//    t.join();
//  }
//
//  @Test(timeout = 5000) public void testConcurrentSizeAndHasAnyValueBounded()
//      throws InterruptedException {
//    final ReplayRelay<Object> rs = ReplayRelay.createWithSize(3);
//    final CyclicBarrier cb = new CyclicBarrier(2);
//
//    Thread t = new Thread(new Runnable() {
//      @Override public void run() {
//        try {
//          cb.await();
//        } catch (InterruptedException e) {
//          return;
//        } catch (BrokenBarrierException e) {
//          return;
//        }
//        for (int i = 0; i < 1000000; i++) {
//          rs.call(i);
//        }
//        System.out.println("Replay fill Thread finished!");
//      }
//    });
//    t.start();
//    try {
//      cb.await();
//    } catch (InterruptedException e) {
//      return;
//    } catch (BrokenBarrierException e) {
//      return;
//    }
//    for (; !rs.hasThrowable() && !rs.hasCompleted(); ) {
//      rs.size(); // can't use value so just call to detect hangs
//      rs.hasAnyValue(); // can't use value so just call to detect hangs
//      Object[] values = rs.getValues();
//      for (int i = 0; i < values.length - 1; i++) {
//        Integer v1 = (Integer) values[i];
//        Integer v2 = (Integer) values[i + 1];
//        assertEquals(1, v2 - v1);
//      }
//    }
//
//    t.join();
//  }
//
//  @Test(timeout = 10000) public void testConcurrentSizeAndHasAnyValueTimeBounded()
//      throws InterruptedException {
//    final ReplayRelay<Object> rs =
//        ReplayRelay.createWithTime(1, TimeUnit.MILLISECONDS, Schedulers.computation());
//    final CyclicBarrier cb = new CyclicBarrier(2);
//
//    Thread t = new Thread(new Runnable() {
//      @Override public void run() {
//        try {
//          cb.await();
//        } catch (InterruptedException e) {
//          return;
//        } catch (BrokenBarrierException e) {
//          return;
//        }
//        for (int i = 0; i < 1000000; i++) {
//          rs.call(i);
//          if (i % 10000 == 0) {
//            try {
//              Thread.sleep(1);
//            } catch (InterruptedException e) {
//              return;
//            }
//          }
//        }
//        System.out.println("Replay fill Thread finished!");
//      }
//    });
//    t.start();
//    try {
//      cb.await();
//    } catch (InterruptedException e) {
//      return;
//    } catch (BrokenBarrierException e) {
//      return;
//    }
//    for (; !rs.hasThrowable() && !rs.hasCompleted(); ) {
//      rs.size(); // can't use value so just call to detect hangs
//      rs.hasAnyValue(); // can't use value so just call to detect hangs
//      Object[] values = rs.getValues();
//      for (int i = 0; i < values.length - 1; i++) {
//        Integer v1 = (Integer) values[i];
//        Integer v2 = (Integer) values[i + 1];
//        assertEquals(1, v2 - v1);
//      }
//    }
//
//    t.join();
//  }
//}
