package com.jakewharton.rxrelay;

import org.junit.Test;
import rx.functions.Action1;
import rx.observers.TestSubscriber;

import static org.junit.Assert.assertFalse;

public final class AsActionTest {

  @Test public void asActionHiding() {
    PublishRelay<Integer> src = PublishRelay.create();

    Action1<Integer> action = src.asAction();

    assertFalse(action instanceof PublishRelay);

    TestSubscriber<Integer> subscriber = new TestSubscriber<Integer>();

    src.subscribe(subscriber);

    action.call(1);

    subscriber.assertValue(1);
    subscriber.assertNoTerminalEvent();
  }
}
