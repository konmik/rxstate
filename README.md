### RxState

This project is implementation of ideas from
[Managing state reactive way](http://konmik.com/post/managing_state_reactive_way/)
article.

##### RxJava1 dependency
 
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.konmik.rxstate/rxstate1/badge.png)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22rxstate1%22)

```groovy
dependencies {
    compile 'com.konmik.rxstate:rxstate1:0.1.0-beta1'
}
```

##### RxJava2 dependency

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.konmik.rxstate/rxstate2/badge.png)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22rxstate2%22)

```groovy
dependencies {
    compile 'com.konmik.rxstate:rxstate2:0.1.0-beta1'
}
```

## Usage

Code (RxJava1):

```java
    RxState<Integer> state = new RxState<>(0, Schedulers.immediate());
    state.values(StartWith.SCHEDULE)
            .subscribe(it -> System.out.println(it));
    state.apply(it -> it + 1);
```

Code (RxJava2):

```java
    RxState<Integer> state = new RxState<>(0, Schedulers.single());
    state.values(StartWith.SCHEDULE)
            .subscribe(it -> System.out.println(it));
    state.apply(it -> it + 1);
```

Prints:

```
0
1
```