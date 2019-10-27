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

import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SerializedRelayTest {

    @Test
    public void testBasic() {
        SerializedRelay<String> subject = new SerializedRelay<String>(PublishRelay.<String> create());
        TestObserver<String> ts = new TestObserver<String>();
        subject.subscribe(ts);
        subject.accept("hello");
        ts.assertValue("hello");
    }

    @Test
    public void testPublishRelayValueRelay() {
        PublishRelay<Integer> async = PublishRelay.create();
        async.accept(1);
        Relay<Integer> serial = async.toSerialized();

        assertFalse(serial.hasObservers());
    }

    @Test
    public void testPublishRelayValueEmpty() {
        PublishRelay<Integer> async = PublishRelay.create();
        Relay<Integer> serial = async.toSerialized();

        assertFalse(serial.hasObservers());
    }

    @Test
    public void testBehaviorRelayValueRelayIncomplete() {
        BehaviorRelay<Integer> async = BehaviorRelay.create();
        async.accept(1);
        Relay<Integer> serial = async.toSerialized();

        assertFalse(serial.hasObservers());
        assertEquals((Integer)1, async.getValue());
        assertTrue(async.hasValue());
        assertArrayEquals(new Object[] { 1 }, async.getValues());
        assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[0]));
        assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[] { 0 }));
        assertArrayEquals(new Integer[] { 1, null }, async.getValues(new Integer[] { 0, 0 }));
    }
    @Test
    public void testBehaviorRelayIncompleteEmpty() {
        BehaviorRelay<Integer> async = BehaviorRelay.create();
        Relay<Integer> serial = async.toSerialized();

        assertFalse(serial.hasObservers());
        assertNull(async.getValue());
        assertFalse(async.hasValue());
        assertArrayEquals(new Object[] { }, async.getValues());
        assertArrayEquals(new Integer[] { }, async.getValues(new Integer[0]));
        assertArrayEquals(new Integer[] { null }, async.getValues(new Integer[] { 0 }));
        assertArrayEquals(new Integer[] { null, 0 }, async.getValues(new Integer[] { 0, 0 }));
    }

    @Test
    public void testReplayRelayValueRelayIncomplete() {
        ReplayRelay<Integer> async = ReplayRelay.create();
        async.accept(1);
        Relay<Integer> serial = async.toSerialized();

        assertFalse(serial.hasObservers());
        assertEquals((Integer)1, async.getValue());
        assertTrue(async.hasValue());
        assertArrayEquals(new Object[] { 1 }, async.getValues());
        assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[0]));
        assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[] { 0 }));
        assertArrayEquals(new Integer[] { 1, null }, async.getValues(new Integer[] { 0, 0 }));
    }

    @Test
    public void testReplayRelayValueRelayBoundedIncomplete() {
        ReplayRelay<Integer> async = ReplayRelay.createWithSize(1);
        async.accept(0);
        async.accept(1);
        Relay<Integer> serial = async.toSerialized();

        assertFalse(serial.hasObservers());
        assertEquals((Integer)1, async.getValue());
        assertTrue(async.hasValue());
        assertArrayEquals(new Object[] { 1 }, async.getValues());
        assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[0]));
        assertArrayEquals(new Integer[] { 1 }, async.getValues(new Integer[] { 0 }));
        assertArrayEquals(new Integer[] { 1, null }, async.getValues(new Integer[] { 0, 0 }));
    }
    @Test
    public void testReplayRelayValueRelayBoundedEmptyIncomplete() {
        ReplayRelay<Integer> async = ReplayRelay.createWithSize(1);
        Relay<Integer> serial = async.toSerialized();

        assertFalse(serial.hasObservers());
        assertNull(async.getValue());
        assertFalse(async.hasValue());
        assertArrayEquals(new Object[] { }, async.getValues());
        assertArrayEquals(new Integer[] { }, async.getValues(new Integer[0]));
        assertArrayEquals(new Integer[] { null }, async.getValues(new Integer[] { 0 }));
        assertArrayEquals(new Integer[] { null, 0 }, async.getValues(new Integer[] { 0, 0 }));
    }
    @Test
    public void testReplayRelayValueRelayEmptyIncomplete() {
        ReplayRelay<Integer> async = ReplayRelay.create();
        Relay<Integer> serial = async.toSerialized();

        assertFalse(serial.hasObservers());
        assertNull(async.getValue());
        assertFalse(async.hasValue());
        assertArrayEquals(new Object[] { }, async.getValues());
        assertArrayEquals(new Integer[] { }, async.getValues(new Integer[0]));
        assertArrayEquals(new Integer[] { null }, async.getValues(new Integer[] { 0 }));
        assertArrayEquals(new Integer[] { null, 0 }, async.getValues(new Integer[] { 0, 0 }));
    }

    @Test
    public void testDontWrapSerializedRelayAgain() {
        PublishRelay<Object> s = PublishRelay.create();
        Relay<Object> s1 = s.toSerialized();
        Relay<Object> s2 = s1.toSerialized();
        assertSame(s1, s2);
    }

    @Test
    public void onNextOnNextRace() {
        for (int i = 0; i < 500; i++) {
            final Relay<Integer> s = PublishRelay.<Integer>create().toSerialized();

            TestObserver<Integer> ts = s.test();

            Runnable r1 = new Runnable() {
                @Override
                public void run() {
                    s.accept(1);
                }
            };

            Runnable r2 = new Runnable() {
                @Override
                public void run() {
                    s.accept(2);
                }
            };

            TestHelper.race(r1, r2, Schedulers.single());

            List<Integer> actual = ts.assertNoErrors().assertNotComplete().values();
            List<Integer> expected = Arrays.asList(1, 2);
            assertTrue("The collections are not the same", actual.size() == expected.size()
                    && actual.containsAll(expected) && expected.containsAll(actual));
        }
    }
}
