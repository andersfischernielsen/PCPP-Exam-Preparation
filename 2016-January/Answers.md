# Answers - 2016

## Question 1

### 1.1

```
Sum is 1599166.000000 and should be 2000000.000000
Sum is 1814240.000000 and should be 2000000.000000
Sum is 1858673.000000 and should be 2000000.000000
Sum is 1858673.000000 and should be 2000000.000000
Sum is 1680947.000000 and should be 2000000.000000
```

The class does not seem to be thread-safe, since the results aren't 2000000.000000.

### 1.2

Since the locking happens on both the instance of TestLocking0 and the static class of TestLocking0, the results are not what we expect.
We get race conditions since the instance is locked in one thread, but the other thread can still continue working, since it is locking on the class object.

### 1.3

We would not lock on both the class object and the instance of the class. We would instead lock only on the instance in btoh threads, so we guarantee thread safety using locking.
The changes can be seen below:

```java
class Mystery {
  private static double sum = 0;

  public synchronized void addStatic(double x) {
    sum += x;
  }

  public synchronized void addInstance(double x) {
    sum += x;
  }

  public synchronized double sum() {
    return sum;
  }
}
```

Results:

```
Sum is 2000000.000000 and should be 2000000.000000
Sum is 2000000.000000 and should be 2000000.000000
Sum is 2000000.000000 and should be 2000000.000000
Sum is 2000000.000000 and should be 2000000.000000
Sum is 2000000.000000 and should be 2000000.000000
```

## Question 2

### 2.1

We would make `get`, `add`, `set`, `toString` synchronized.
This is the simplest way to make the class thread-safe in our opinion.
We don't need to make `size` synchronized, since the `size` member on the class is read-only in this implementation and therefore cannot lead to race conditions.

### 2.2

This simple thread-safe implementation would not scale very well, since we lock the entire array of items whenever a thread calls either of `get`, `add`, `set`, `toString`.

### 2.3

Threads will wait for each other while trying to do each single operation, but this does not achieve thread-safety since it is possible to insert into the list while fetching an object out of the list, which will lead to race-conditions.
Visibility is broken in this implementation since it is possible for one thread to insert while another either fetches an object or tries to get the size. These actions can happen concurrently, but the thread getting size or an object in the list might not see the update.

### 2.4

Using striping where we lock $n$ sections of the inner array on all operations would give us better performance and guarantee visibility.

## Question 3

### 3.1
By using an AtomicInteger and calling it's getAndIncrement method, we can ensure that totalSize is maintained correctly.

### 3.2 
The `DoubleArrayList` can be made thread-safe by locking on the local variable inside the class in the constructor, like so:

```java
  class DoubleArrayList {
  private static AtomicInteger totalSize = new AtomicInteger();
  
  [...]

  public DoubleArrayList() {
    synchronized (allLists) {
      allLists.add(this);
    }
  }

  [...] 

  public boolean add(double x) {
    [...]
    size++;
    totalSize.getAndIncrement();
    return true;
  }

  [...] 

  public static int totalSize() {
    return totalSize.get();
  }
```

## Question 4

#### 4.1
SortingStage: 
```java
static class SortingStage implements Runnable {
  BlockingDoubleQueue input, output;
  int itemCount, heapSize;
  double[] heap;

  public SortingStage(int itemCount, BlockingDoubleQueue input, int s) {
    this.input = input;
    this.output = new WrappedArrayDoubleQueue();
    this.itemCount = itemCount;
    this.heap = new double[s];
    this.heapSize = 0;
  }

  public void run() {
    while (itemCount > 0) {
      double x = input.take();
      if (heapSize < heap.length) {
        heap[heapSize++] = x;
        DoubleArray.minheapSiftup(heap, heapSize - 1, heapSize - 1);
      } else if (x <= heap[0]) {
        output.put(x);
        itemCount--;
      } else {
        double least = heap[0];
        heap[0] = x;
        DoubleArray.minheapSiftdown(heap, 0, heapSize - 1);
        output.put(least);
        itemCount--;
      }
    }
  }
}
```

### 4.2 
sortPipeline
```java
private static void sortPipeline(double[] arr, int P, BlockingDoubleQueue[] queues) {
  int S = arr.length / P;
  Thread[] threads = new Thread[P + 2];
  SortingStage[] sortingStages = new SortingStage[P + 2];
  DoubleGenerator dg = new DoubleGenerator(arr, arr.length, new WrappedArrayDoubleQueue());
  for (int i = 0; i < threads.length; i++) {
    if (i == 0)
      threads[i] = new Thread(dg); // initial double generator
    else if (i == 1) {
      SortingStage ss = new SortingStage(arr.length + (P - i) * S, dg.output, S);
      sortingStages[i] = ss;
      threads[i] = new Thread(ss);
    } else if (i == threads.length - 1) { // The sorted checker
      threads[i] = new Thread(new SortedChecker(arr.length, sortingStages[i - 1].output));
    } else { // all stages between first and last
      SortingStage ss = new SortingStage(arr.length + (P - i) * S, sortingStages[i - 1].output, S);
      sortingStages[i] = ss;
      threads[i] = new Thread(ss);
    }
  }

  for (int i = 0; i < threads.length; i++) {
    threads[i].start();
  }
  try {
    for (int i = 0; i < threads.length; i++) {
      threads[i].join();
    }
  } catch (InterruptedException e) {
  }
}
```

## Question 5 

### 5.1
WrappedArrayDoubleQueue
```java
class WrappedArrayDoubleQueue implements BlockingDoubleQueue {
  ArrayBlockingQueue arrayBlockingQueue;

  public WrappedArrayDoubleQueue() {
    this.arrayBlockingQueue = new ArrayBlockingQueue<Double>(50);
  }

  public double take() {
    try {
      return (double) arrayBlockingQueue.take();
    } catch (InterruptedException e) {
      return -1.0;
    }
  }

  public void put(double item) {
    try {
      arrayBlockingQueue.put(item);
    } catch (InterruptedException e) {
    }
  }
}
```
### 5.2
```
# OS:   Mac OS X; 10.14.1; x86_64
# JVM:  Oracle Corporation; 1.8.0_151
# CPU:  null; 4 "cores"
# Date: 2018-12-12T21:34:45+0100
0.1 1.1 2.1 3.1 4.1 5.1 6.1 7.1 8.1 9.1 10.1 11.1 12.1 13.1 14.1 15.1 16.1 17.1 18.1 19.1 20.1 21.1 22.1 23.1 24.1 25.1 26.1 27.1 28.1 29.1 30.1 31.1 32.1 33.1 34.1 35.1 36.1 37.1 38.1 39.1
```

### 5.3
```
sortPipeLine                        179,0 ms      21,59          2
```
**DISCUSS THE RESULTS?**

## Question 6

### 6.1
```java
class BlockingNDoubleQueue implements BlockingDoubleQueue {
  double[] items;
  int head, tail, currentSize;

  BlockingNDoubleQueue() {
    this.items = new double[50];
    this.head = 0;
    this.tail = 0;
    this.currentSize = 0;
  }

  public double take() {
    synchronized (this) {
      while (currentSize == 0) {
        try {
          this.wait();
        } catch (InterruptedException e) {
        }
      }
      double item = items[head];
      if (head < 50 - 1)
        head++;
      currentSize--;
      this.notifyAll();
      return item;
    }
  }

  public void put(double item) {
    synchronized (this) {
      while (currentSize == 49) {
        try {
          this.wait();
        } catch (InterruptedException e) {
        }
      }
      reallocate();
      items[tail] = item;
      if (tail < 50 - 1)
        tail++;
      currentSize++;
      this.notifyAll();
    }
  }

  private void reallocate() {
    if (tail == items.length - 1) {
      for (int h = head, i = 0; h < items.length; h++, i++) {
        items[i] = items[h];
      }
      tail -= head; // Update tail.
      head = 0; // Update head.
    }
  }
```

### 6.2
It's thread safe because both the `put` and the `take` methods are synchronized on the object. This means that every time either of these two methods are invoked, only one thread can work at a time. Note that `reallocate` doesn't have to be synchronized since it's invoked within `put` inside a synchronized block. 

### 6.3
```
# OS:   Mac OS X; 10.14.1; x86_64
# JVM:  Oracle Corporation; 1.8.0_151
# CPU:  null; 4 "cores"
# Date: 2018-12-13T14:35:12+0100
sortPipeLine                        711,7 ms      85,39          2
```
**Discuss the results?!**

## Question 7

### 7.1
```java
class UnboundedBlockingQueue implements BlockingDoubleQueue {
  private static class Node {
    final double item;
    Node next;

    public Node(double item, Node next) {
      this.item = item;
      this.next = next;
    }
  }

  private Node head, tail;

  public UnboundedBlockingQueue() {
    head = tail = new Node(0, null);
  }

  public void put(double item) { // at tail
    synchronized (this) {
      Node node = new Node(item, null);
      tail.next = node;
      tail = node;
      this.notifyAll();
    }
  }

  public double take() { // from head
    synchronized (this) {
      while (head.next == null) {
        try {
          this.wait();
        } catch (InterruptedException e) {
        }
      }
      Node first = head;
      head = first.next;
      return head.item;
    }
  }
}
```

### 7.2
It's thread safe because both the `put` and the `take` methods are synchronized on the object. This means that every time either of these two methods are invoked, only one thread can work at a time. Furthermore `take` is blocking when the queue is empty, and thus `put` needs to notify when a new item is added.

### 7.3
```
# OS:   Mac OS X; 10.14.1; x86_64
# JVM:  Oracle Corporation; 1.8.0_151
# CPU:  null; 4 "cores"
# Date: 2018-12-13T15:30:42+0100
sortPipeLine                        230,9 ms      15,62          2
```
**DISCUSS THE RESULT**

## Question 8

### 8.1
```java
class NolockNDoubleQueue implements BlockingDoubleQueue {
  volatile int head, tail;
  final double[] items;

  public NolockNDoubleQueue() {
    this.head = 0;
    this.tail = 0;
    this.items = new double[50];
  }

  public void put(double item) {
    while (tail - head == items.length) {
    }
    items[tail % items.length] = item;
    tail++;
  }

  public double take() {
    while (tail - head == 0) {
    }
    double out = items[head % items.length];
    head++;
    return out;
  }
}
```

### 8.2
`items` is declared final, because it ensures visibility and also we never want to change the reference.
`head` and `tail` needs to be declared volatile to ensure visibility. Each thread must be able to see the changing `head` and `tail` values. 

### 8.3
**To be done**

### 8.4
The while loop makes sure that each thread waits for the other one. This only works if one thread only puts and another only takes. If a thread did both we would the thread would become stuck in the while loops and thus never terminate.
The memory writes are visible to all threads, because `head` and `tail` has been declared volatile.

### 8.5
When it reaches 50.1 the method (might have something to do with the capacity, it gets stuck). But why?

### 8.6
```
# OS:   Mac OS X; 10.14.1; x86_64
# JVM:  Oracle Corporation; 1.8.0_151
# CPU:  null; 4 "cores"
# Date: 2018-12-13T19:54:25+0100
sortPipeLine                      17576,5 ms     506,94          2
```
**DISCUSS**

### 8.7
**fucking doesn't terminate**


## Question 9

### 9.1
```java
class MSUnboundedDoubleQueue implements BlockingDoubleQueue {
  private final AtomicReference<Node> head, tail;

  public MSUnboundedDoubleQueue() {
    Node dummy = new Node(0, null);
    head = new AtomicReference<Node>(dummy);
    tail = new AtomicReference<Node>(dummy);
  }

  public void put(double item) { // at tail
    Node node = new Node(item, null);
    Node last = tail.get(), next = last.next.get();
    // if (last == tail.get()) { // E7
    if (next == null) {
      // In quiescent state, try inserting new node
      if (last.next.compareAndSet(next, node)) { // E9
        // Insertion succeeded, try advancing tail
        tail.compareAndSet(last, node);
        return;
      }
    } else
      // Queue in intermediate state, advance tail
      tail.compareAndSet(last, next);
    // }
  }

  public double take() { // from head
    while (true) {
      Node first = head.get(), last = tail.get(), next = first.next.get(); // D3
      // if (first == head.get()) { // D5
      if (first == last) {
        if (next != null)
          tail.compareAndSet(last, next);
      } else {
        double result = next.item;
        if (head.compareAndSet(first, next)) {// D13
          return result;
        }
      }
      // }
    }
  }

  private static class Node {
    final double item;
    final AtomicReference<Node> next;

    public Node(double item, Node next) {
      this.item = item;
      this.next = new AtomicReference<Node>(next);
    }
  }
}
```
### 9.2
Compare and Swap (or Compare and Set) is always thread safe, due to the method actually checking if a value has changed. If a value has changed, the current thread helps another thread to increment head, and then tries again. It does this until a value hasn't changed (is as expected), and then it changes the value. Thus this is always thread safe.

### 9.3
```
# OS:   Mac OS X; 10.14.1; x86_64
# JVM:  Oracle Corporation; 1.8.0_151
# CPU:  null; 4 "cores"
# Date: 2018-12-13T19:46:15+0100
sortPipeLine                         76,9 ms       9,37          4
```
This is even faster than the native java queue, but as we saw in previous exercises the MSQueue with compare and swap scales less efficiently than the native methods, when congestion gets more dense.

## Question 10

### 10.1
```java
class StmBlockingNDoubleQueue implements BlockingDoubleQueue {
  private final TxnInteger availableItems, availableSpaces;
  private final TxnRef<Double>[] items;
  private final TxnInteger head, tail;

  public StmBlockingNDoubleQueue() {
    this.availableItems = newTxnInteger(0);
    this.availableSpaces = newTxnInteger(50);
    this.items = makeArray(50);
    for (int i = 0; i < 50; i++)
      this.items[i] = StmUtils.<Double>newTxnRef();
    this.head = newTxnInteger(0);
    this.tail = newTxnInteger(0);
  }

  public void put(double item) { // at tail
    atomic(() -> {
      if (availableSpaces.get() == 0)
        retry();
      else {
        availableSpaces.decrement();
        items[tail.get()].set(item);
        tail.set((tail.get() + 1) % items.length);
        availableItems.increment();
      }
    });
  }

  public double take() { // from head
    return atomic(() -> {
      if (availableItems.get() == 0) {
        retry();
        return null; // unreachable
      } else {
        availableItems.decrement();
        double item = items[head.get()].get();
        items[head.get()].set(null);
        head.set((head.get() + 1) % items.length);
        availableSpaces.increment();
        return item;
      }
    });
  }

  @SuppressWarnings("unchecked")
  private static <Double> TxnRef<Double>[] makeArray(int capacity) {
    // Java's @$#@?!! type system requires this unsafe cast
    return (TxnRef<Double>[]) new TxnRef[capacity];
  }
}
```
### 10.2
It is optimistic conccurency. So you atempt to either put or take a value into the queue, and wraps the entire method in a `atomic` block. Thus if anything goes wrong, the method attempt to rerun in 2^x time, where x is the number of tries. This ensures thread safety. 

### 10.3
```
# OS:   Mac OS X; 10.14.2; x86_64
# JVM:  Oracle Corporation; 1.8.0_151
# CPU:  null; 4 "cores"
# Date: 2018-12-14T10:46:52+0100
dec. 14, 2018 10:46:52 AM org.multiverse.api.GlobalStmInstance <clinit>
INFO: Initializing GlobalStmInstance using factoryMethod 'org.multiverse.stms.gamma.GammaStm.createFast'.
dec. 14, 2018 10:46:52 AM org.multiverse.api.GlobalStmInstance <clinit>
INFO: Successfully initialized GlobalStmInstance using factoryMethod 'org.multiverse.stms.gamma.GammaStm.createFast'.
sortPipeLine                       1137,2 ms     183,54          2
```

## Question 11

