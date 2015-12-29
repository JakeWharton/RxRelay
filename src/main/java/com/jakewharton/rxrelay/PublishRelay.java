package com.jakewharton.rxrelay;

import com.jakewharton.rxrelay.RelaySubscriptionManager.RelayObserver;
import rx.Observer;

/**
 * Relay that, once an {@link Observer} has subscribed, emits all subsequently observed items to
 * the subscriber.
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code
 * PublishRelay<Object> relay = PublishRelay.create();
 * // observer1 will receive all events
 * relay.subscribe(observer1);
 * relay.call("one");
 * relay.call("two");
 *
 * // observer2 will only receive "three"
 * relay.subscribe(observer2);
 * relay.call("three");
 * } </pre>
 *
 * @param <T> the type of items observed and emitted by the Relay
 */
public class PublishRelay<T> extends Relay<T, T> {
  /**
   * Creates and returns a new {@code PublishRelay}.
   *
   * @param <T> the value type
   * @return the new {@code PublishRelay}
   */
  public static <T> PublishRelay<T> create() {
    RelaySubscriptionManager<T> state = new RelaySubscriptionManager<T>();
    return new PublishRelay<T>(state, state);
  }

  private final RelaySubscriptionManager<T> state;

  protected PublishRelay(OnSubscribe<T> onSubscribe, RelaySubscriptionManager<T> state) {
    super(onSubscribe);
    this.state = state;
  }

  @Override public void call(T v) {
    for (RelayObserver<T> ro : state.observers()) {
      ro.onNext(v);
    }
  }

  @Override public boolean hasObservers() {
    return state.observers().length > 0;
  }
}
