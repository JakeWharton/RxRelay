RxRelay
=======

Objects that are both an `Observable` and an `Action1`.

Basically: A `Subject` except without the ability to call `onComplete` or `onError`.



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



 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
