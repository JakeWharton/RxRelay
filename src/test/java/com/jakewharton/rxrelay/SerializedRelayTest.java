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

import java.util.Collections;
import org.junit.Test;
import rx.observers.TestSubscriber;

import static org.junit.Assert.assertSame;

public final class SerializedRelayTest {
  @Test public void testBasic() {
    SerializedRelay<String, String> relay =
        new SerializedRelay<String, String>(PublishRelay.<String>create());
    TestSubscriber<String> ts = new TestSubscriber<String>();
    relay.subscribe(ts);
    relay.call("hello");
    ts.assertReceivedOnNext(Collections.singletonList("hello"));
  }

  @Test public void testDontWrapSerializedRelayAgain() {
    PublishRelay<Object> s = PublishRelay.create();
    Relay<Object, Object> s1 = s.toSerialized();
    Relay<Object, Object> s2 = s1.toSerialized();
    assertSame(s1, s2);
  }
}
