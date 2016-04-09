package com.jakewharton.rxrelay;

import org.junit.Test;

import rx.Observer;
import rx.functions.Action1;

import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public final class AsActionTest {
    @Test public void testHiding() {
        PublishRelay<Integer> src = PublishRelay.create();

        Action1<Integer> action = src.asAction();

        assertFalse(action instanceof PublishRelay);

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        src.subscribe(o);

        action.call(1);

        verify(o).onNext(1);
        verify(o, never()).onCompleted();
        verify(o, never()).onError(any(Throwable.class));
    }
}
