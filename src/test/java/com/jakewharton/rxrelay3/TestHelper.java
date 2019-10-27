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
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.exceptions.CompositeException;
import io.reactivex.rxjava3.internal.util.ExceptionHelper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Common methods for helping with tests from 1.x mostly.
 */
public enum TestHelper {
    ;

    /**
     * Mocks an Observer with the proper receiver type.
     * @param <T> the value type
     * @return the mocked observer
     */
    @SuppressWarnings("unchecked")
    public static <T> io.reactivex.rxjava3.core.Observer<T> mockObserver() {
        return mock(io.reactivex.rxjava3.core.Observer.class);
    }

    /**
     * Synchronizes the execution of two runnables (as much as possible)
     * to test race conditions.
     * <p>The method blocks until both have run to completion.
     * @param r1 the first runnable
     * @param r2 the second runnable
     * @param s the scheduler to use
     */
    public static void race(final Runnable r1, final Runnable r2, Scheduler s) {
        final AtomicInteger count = new AtomicInteger(2);
        final CountDownLatch cdl = new CountDownLatch(2);

        final Throwable[] errors = { null, null };

        s.scheduleDirect(new Runnable() {
            @Override
            public void run() {
                if (count.decrementAndGet() != 0) {
                    while (count.get() != 0) { }
                }

                try {
                    try {
                        r1.run();
                    } catch (Throwable ex) {
                        errors[0] = ex;
                    }
                } finally {
                    cdl.countDown();
                }
            }
        });

        if (count.decrementAndGet() != 0) {
            while (count.get() != 0) { }
        }

        try {
            try {
                r2.run();
            } catch (Throwable ex) {
                errors[1] = ex;
            }
        } finally {
            cdl.countDown();
        }

        try {
            if (!cdl.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("The wait timed out!");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        if (errors[0] != null && errors[1] == null) {
            throw ExceptionHelper.wrapOrThrow(errors[0]);
        }

        if (errors[0] == null && errors[1] != null) {
            throw ExceptionHelper.wrapOrThrow(errors[1]);
        }

        if (errors[0] != null && errors[1] != null) {
            throw new CompositeException(errors);
        }
    }

    /**
     * Checks if the upstream's Disposable sent through the onSubscribe reports
     * isDisposed properly before and after calling dispose.
     * @param source the source to test
     */
    public static void checkDisposed(io.reactivex.rxjava3.core.Observable<?> source) {
        final Boolean[] b = { null, null };
        final CountDownLatch cdl = new CountDownLatch(1);
        source.subscribe(new io.reactivex.rxjava3.core.Observer<Object>() {

            @Override public void onSubscribe(Disposable d) {
                try {
                    b[0] = d.isDisposed();

                    d.dispose();

                    b[1] = d.isDisposed();

                    d.dispose();
                } finally {
                    cdl.countDown();
                }
            }

            @Override public void onNext(Object value) {
                // ignored
            }

            @Override public void onError(Throwable e) {
                // ignored
            }

            @Override public void onComplete() {
                // ignored
            }
        });

        try {
            assertTrue("Timed out", cdl.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            throw ExceptionHelper.wrapOrThrow(ex);
        }

        assertEquals("Reports disposed upfront?", false, b[0]);
        assertEquals("Didn't report disposed after?", true, b[1]);
    }
}
