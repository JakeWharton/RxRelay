RxRelay
=======

Relays are [RxJava][rx] types which are both an `Observable` and an `Action1`.

Basically: A `Subject` except without the ability to call `onComplete` or `onError`.

Subjects are useful to bridge the gap between non-Rx APIs. However, they are stateful in a damaging
way: when they receive an `onComplete` or `onError` they no longer become usable for moving data.
This is the observable contract and sometimes it is the desired behavior. Most times it is not.

Relays are simply Subjects without the aforementioned property. They allow you to bridge non-Rx
APIs into Rx easily, and without the worry of accidentally triggering a terminal state.

As more of your code moves to reactive, the need for Subjects and Relays should diminish. In the
transitional period, or for quickly adapting a non-Rx API, Relays provide the convenience of
Subjects without the worry of the statefulness of terminal event behavior.



Download
--------

Maven:
```xml
<dependency>
  <groupId>com.jakewharton.rxrelay</groupId>
  <artifactId>rxrelay</artifactId>
  <version>1.0.0</version>
</dependency>
```
Gradle:
```groovy
compile 'com.jakewharton.rxrelay:rxrelay:1.0.0'
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
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
