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
import io.reactivex.rxjava3.annotations.CheckReturnValue;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * Represents a Consumer and an Observable at the same time, allowing
 * multicasting events from a single source to multiple child Observers.
 * <p>All methods except {@link #accept} are thread-safe.
 * Use {@link #toSerialized()} to make it thread-safe as well.
 *
 * @param <T> the item value type
 */
public abstract class Relay<T> extends Observable<T> implements Consumer<T> {
    /** {@inheritDoc} */
    @Override public abstract void accept(@NonNull T value); // Redeclare without checked exception.

    /**
     * Returns true if the subject has any Observers.
     * <p>The method is thread-safe.
     */
    public abstract boolean hasObservers();

    /**
     * Wraps this Relay and serializes the calls to {@link #accept}, making it thread-safe.
     * <p>The method is thread-safe.
     */
    @NonNull
    @CheckReturnValue
    public final Relay<T> toSerialized() {
        if (this instanceof SerializedRelay) {
            return this;
        }
        return new SerializedRelay<T>(this);
    }
}
