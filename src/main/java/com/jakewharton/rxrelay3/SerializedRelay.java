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

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observer;

/**
 * Serializes calls to the Consumer methods.
 * <p>All other Relay methods are thread-safe by design.
 */
/* public */ final class SerializedRelay<T> extends Relay<T> {
    /** The actual subscriber to serialize Subscriber calls to. */
    private final Relay<T> actual;
    /** Indicates an emission is going on, guarded by this. */
    private boolean emitting;
    /** If not null, it holds the missed NotificationLite events. */
    private AppendOnlyLinkedArrayList<T> queue;

    /**
     * Constructor that wraps an actual relay.
     */
    SerializedRelay(final Relay<T> actual) {
        this.actual = actual;
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        actual.subscribe(observer);
    }


    @Override
    public void accept(@NonNull T value) {
        synchronized (this) {
            if (emitting) {
                AppendOnlyLinkedArrayList<T> q = queue;
                if (q == null) {
                    q = new AppendOnlyLinkedArrayList<T>(4);
                    queue = q;
                }
                q.add(value);
                return;
            }
            emitting = true;
        }
        actual.accept(value);
        emitLoop();
    }

    /** Loops until all notifications in the queue has been processed. */
    private void emitLoop() {
        for (;;) {
            AppendOnlyLinkedArrayList<T> q;
            synchronized (this) {
                q = queue;
                if (q == null) {
                    emitting = false;
                    return;
                }
                queue = null;
            }
            q.accept(actual);
        }
    }

    @Override
    public boolean hasObservers() {
        return actual.hasObservers();
    }
}
