RxRelay
=======

Relays are [RxJava][rx] types which are both an `Observable` and a `Consumer`.

Basically: A `Subject` except without the ability to call `onComplete` or `onError`.

Subjects are useful to bridge the gap between non-Rx APIs. However, they are stateful in a damaging
way: when they receive an `onComplete` or `onError` they no longer become usable for moving data.
This is the observable contract and sometimes it is the desired behavior. Most times it is not.

Relays are simply Subjects without the aforementioned property. They allow you to bridge non-Rx
APIs into Rx easily, and without the worry of accidentally triggering a terminal state.

As more of your code moves to reactive, the need for Subjects and Relays should diminish. In the
transitional period, or for quickly adapting a non-Rx API, Relays provide the convenience of
Subjects without the worry of the statefulness of terminal event behavior.


Usage
-----

 *  **`BehaviorRelay`**

    Relay that emits the most recent item it has observed and all subsequent observed items to each
    subscribed `Observer`.

    ```java
    // observer will receive all events.
    BehaviorRelay<Object> relay = BehaviorRelay.createDefault("default");
    relay.subscribe(observer);
    relay.accept("one");
    relay.accept("two");
    relay.accept("three");
    ```
    ```java
    // observer will receive the "one", "two" and "three" events, but not "zero"
    BehaviorRelay<Object> relay = BehaviorRelay.createDefault("default");
    relay.accept("zero");
    relay.accept("one");
    relay.subscribe(observer);
    relay.accept("two");
    relay.accept("three");
    ```

 *  **`PublishRelay`**

    Relay that, once an `Observer` has subscribed, emits all subsequently observed items to the
    subscriber.

    ```java
    PublishRelay<Object> relay = PublishRelay.create();
    // observer1 will receive all events
    relay.subscribe(observer1);
    relay.accept("one");
    relay.accept("two");
    // observer2 will only receive "three"
    relay.subscribe(observer2);
    relay.accept("three");
    ```

 *  **`ReplayRelay`**

    Relay that buffers all items it observes and replays them to any `Observer` that subscribes.

    ```java
    ReplayRelay<Object> relay = ReplayRelay.create();
    relay.accept("one");
    relay.accept("two");
    relay.accept("three");
    // both of the following will get the events from above
    relay.subscribe(observer1);
    relay.subscribe(observer2);
    ```

All relays use the `Relay` base class which also allows custom implementations.

See [the Javadoc][docs] for more information.

(There is no `AsyncRelay` since relays have no terminal events to support its behavior.)



Download
--------

Gradle:
```groovy
implementation 'com.jakewharton.rxrelay3:rxrelay:3.0.1'
```
Maven:
```xml
<dependency>
  <groupId>com.jakewharton.rxrelay3</groupId>
  <artifactId>rxrelay</artifactId>
  <version>3.0.1</version>
</dependency>
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].


License
-------

    Copyright 2014 Netflix, Inc.
    Copyright 2015 Jake Wharton

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.



 [rx]: https://github.com/ReactiveX/RxJava/
 [docs]: http://jakewharton.github.io/RxRelay/
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
