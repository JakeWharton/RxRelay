Change Log
==========

Version 2.0.0 *(2016-11-29)*
----------------------------

This version only supports RxJava 2.

 * New: Maven coordinates are now `com.jakewharton.rxrelay2:rxrelay`. Package name is now
   `com.jakewharton.rxrelay2.*`.


Version 1.2.0 *(2016-10-06)*
----------------------------

 * New: Remove `@Beta` annotation on methods to reflect RxJava 1.2.0 changes.
 * Fix: Remove dependency on RxJava internal classes which might break across its releases.


Version 1.1.0 *(2016-03-10)*
----------------------------

 * New: `asAction()` method exposes a `Relay` solely as an `Action1`.


Version 1.0.0 *(2015-12-29)*
----------------------------

Initial import from `Subject` and friends.
