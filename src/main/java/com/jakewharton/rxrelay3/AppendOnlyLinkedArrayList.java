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

import io.reactivex.rxjava3.functions.Predicate;

/**
 * A linked-array-list implementation that only supports appending and consumption.
 *
 * @param <T> the value type
 */
class AppendOnlyLinkedArrayList<T> {
    private final int capacity;
    private final Object[] head;
    private Object[] tail;
    private int offset;

    /**
     * Constructs an empty list with a per-link capacity.
     * @param capacity the capacity of each link
     */
    AppendOnlyLinkedArrayList(int capacity) {
        this.capacity = capacity;
        this.head = new Object[capacity + 1];
        this.tail = head;
    }

    /**
     * Append a non-null value to the list.
     * <p>Don't add null to the list!
     * @param value the value to append
     */
    void add(T value) {
        final int c = capacity;
        int o = offset;
        if (o == c) {
            Object[] next = new Object[c + 1];
            tail[c] = next;
            tail = next;
            o = 0;
        }
        tail[o] = value;
        offset = o + 1;
    }

    /**
     * Predicate interface suppressing the exception.
     *
     * @param <T> the value type
     */
    public interface NonThrowingPredicate<T> extends Predicate<T> {
        @Override
        boolean test(T t);
    }

    /**
     * Loops over all elements of the array until a null element is encountered or
     * the given predicate returns true.
     * @param consumer the consumer of values that returns true if the forEach should terminate
     */
    @SuppressWarnings("unchecked")
    void forEachWhile(NonThrowingPredicate<? super T> consumer) {
        Object[] a = head;
        final int c = capacity;
        while (a != null) {
            for (int i = 0; i < c; i++) {
                Object o = a[i];
                if (o == null) {
                    break;
                }
                if (consumer.test((T)o)) {
                    break;
                }
            }
            a = (Object[])a[c];
        }
    }

    @SuppressWarnings("unchecked")
    void accept(Relay<? super T> observer) {
        Object[] a = head;
        final int c = capacity;
        while (a != null) {
            for (int i = 0; i < c; i++) {
                Object o = a[i];
                if (o == null) {
                    break;
                }

                observer.accept((T) o);
            }
            a = (Object[])a[c];
        }
    }
}
