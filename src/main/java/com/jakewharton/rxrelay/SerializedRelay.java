package com.jakewharton.rxrelay;

import rx.Subscriber;

public class SerializedRelay<T, R> extends Relay<T, R> {
  private final SerializedAction1<T> action;
  private final Relay<T, R> actual;

  public SerializedRelay(final Relay<T, R> actual) {
    super(new OnSubscribe<R>() {
      @Override public void call(Subscriber<? super R> child) {
        actual.unsafeSubscribe(child);
      }
    });
    this.actual = actual;
    this.action = new SerializedAction1<T>(actual);
  }

  @Override public void call(T t) {
    action.call(t);
  }

  @Override public boolean hasObservers() {
    return actual.hasObservers();
  }
}
