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

import rx.functions.Action1;

/**
 * Enforces single-threaded, serialized, ordered execution of {@link #call}.
 * <p>
 * When multiple threads are emitting and/or notifying they will be serialized by:
 * </p><ul>
 * <li>Allowing only one thread at a time to emit</li>
 * <li>Adding notifications to a queue if another thread is already emitting</li>
 * <li>Not holding any locks or blocking any threads while emitting</li>
 * </ul>
 *
 * @param <T> the type of items expected to be observed by the {@code Observer}
 */
final class SerializedAction1<T> implements Action1<T> {
  private final Action1<? super T> actual;

  private boolean emitting;
  /** If not null, it indicates more work. */
  private FastList<T> queue;

  /** Number of iterations without additional safepoint poll in the drain loop. */
  private static final int MAX_DRAIN_ITERATION = 1024;

  private static final class FastList<T> {
    T[] array;
    int size;

    FastList() {
    }

    void add(T o) {
      int s = size;
      T[] a = array;
      if (a == null) {
        a = (T[]) new Object[16];
        array = a;
      } else if (s == a.length) {
        T[] array2 = (T[]) new Object[s + (s >> 2)];
        System.arraycopy(a, 0, array2, 0, s);
        a = array2;
        array = a;
      }
      a[s] = o;
      size = s + 1;
    }
  }

  SerializedAction1(Action1<? super T> s) {
    this.actual = s;
  }

  @Override public void call(T t) {
    synchronized (this) {
      if (emitting) {
        FastList<T> list = queue;
        if (list == null) {
          list = new FastList<T>();
          queue = list;
        }
        list.add(t);
        return;
      }
      emitting = true;
    }

    actual.call(t);

    for (; ; ) {
      for (int i = 0; i < MAX_DRAIN_ITERATION; i++) {
        FastList<T> list;
        synchronized (this) {
          list = queue;
          if (list == null) {
            emitting = false;
            return;
          }
          queue = null;
        }
        for (T o : list.array) {
          if (o == null) {
            break;
          }
          actual.call(o);
        }
      }
    }
  }
}
