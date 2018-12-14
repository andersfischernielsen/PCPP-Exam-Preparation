// Pipelined sorting using P>=1 stages, each maintaining an internal
// collection of size S>=1.  Stage 1 contains the largest items, stage
// 2 the second largest, ..., stage P the smallest ones.  In each
// stage, the internal collection of items is organized as a minheap.
// When a stage receives an item x and its collection is not full, it
// inserts it in the heap.  If the collection is full and x is less
// than or equal to the collections's least item, it forwards the item
// to the next stage; otherwise forwards the collection's least item
// and inserts x into the collection instead.

// When there are itemCount items and stageCount stages, each stage
// must be able to hold at least ceil(itemCount/stageCount) items,
// which equals (itemCount-1)/stageCount+1.

// Compile and run like this:
//   javac -cp ~/lib/multiverse-core-0.7.0.jar TestStmQueues.java 
//   java -cp ~/lib/multiverse-core-0.7.0.jar:. TestStmQueues

// sestoft@itu.dk * 2016-01-10

import java.util.stream.DoubleStream;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.IntToDoubleFunction;
import java.util.concurrent.atomic.AtomicReference;

//multiverse
import org.multiverse.api.Txn;
import org.multiverse.api.references.*;
import org.multiverse.api.callables.*;
import org.multiverse.api.GlobalStmInstance;
import org.multiverse.api.StmUtils;
import static org.multiverse.api.StmUtils.*;

public class SortingPipeline {
  public static void main(String[] args) {
    SystemInfo();
    final int count = 100_000, P = 4;
    // final int count = 8, P = 2;
    final double[] arr = DoubleArray.randomPermutation(count);
    BlockingDoubleQueue[] queues = new BlockingDoubleQueue[P + 1];
    for (int i = 0; i < queues.length; i++) {
      // queues[i] = new WrappedArrayDoubleQueue();
      // queues[i] = new BlockingNDoubleQueue();
      // queues[i] = new UnboundedBlockingQueue();
      // queues[i] = new NolockNDoubleQueue();
      // queues[i] = new MSUnboundedDoubleQueue();
      queues[i] = new StmBlockingNDoubleQueue();

    }
    Mark7("sortPipeLine", i -> sortPipeline(arr, P, queues)); // I'm in doubt
    // wether this the correct way to do it.
    // sortPipeline(arr, P, queues);
  }

  private static double sortPipeline(double[] arr, int P, BlockingDoubleQueue[] queues) {
    // test with DoubleNQueue
    // DoubleGenerator dg = new DoubleGenerator(arr, 8, new BlockingNDoubleQueue());
    // dg.run();
    // BlockingNDoubleQueue q1 = (BlockingNDoubleQueue) dg.output;
    // q1.printQueue();
    // SortingStage ss1 = new SortingStage(12, q1, 4);
    // ss1.run();
    // BlockingNDoubleQueue q2 = (BlockingNDoubleQueue) ss1.output;
    // q2.printQueue();
    // SortingStage ss2 = new SortingStage(8, q2, 4);
    // ss2.run();
    // BlockingNDoubleQueue q3 = (BlockingNDoubleQueue) ss2.output;
    // q3.printQueue();
    // SortedChecker sc = new SortedChecker(8, q3);
    // sc.run();

    // THIS WORKS SEQUENTIALLY! 8 elements, 2 sorting stages, 3 queues.
    // DoubleGenerator dg = new DoubleGenerator(arr, 8, new
    // WrappedArrayDoubleQueue());
    // dg.run();
    // System.out.println(dg.output);
    // SortingStage ss1 = new SortingStage(12, dg.output, 4);
    // ss1.run();
    // System.out.println(ss1.output);
    // SortingStage ss2 = new SortingStage(8, ss1.output, 4);
    // ss2.run();
    // System.out.println(ss2.output);
    // SortedChecker sc = new SortedChecker(8, ss2.output);
    // sc.run();

    // messy code, but works.
    int S = arr.length / P;
    Thread[] threads = new Thread[P + 2];
    SortingStage[] sortingStages = new SortingStage[P + 2];
    // DoubleGenerator dg = new DoubleGenerator(arr, arr.length, new
    // WrappedArrayDoubleQueue());
    DoubleGenerator dg = new DoubleGenerator(arr, arr.length, new StmBlockingNDoubleQueue());
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
    return 0; // modified sortPipeLine to be compatible with mark7.
  }

  static class SortingStage implements Runnable {
    // TO DO: field declarations, constructor, and so on
    BlockingDoubleQueue input, output;
    int itemCount, heapSize;
    double[] heap;

    public SortingStage(int itemCount, BlockingDoubleQueue input, int s) {
      this.input = input;
      // this.output = new WrappedArrayDoubleQueue();
      // this.output = new BlockingNDoubleQueue();
      // this.output = new UnboundedBlockingQueue();
      // this.output = new NolockNDoubleQueue();
      // this.output = new MSUnboundedDoubleQueue();
      this.output = new StmBlockingNDoubleQueue();
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

  static class DoubleGenerator implements Runnable {
    private final BlockingDoubleQueue output;
    private final double[] arr; // The numbers to feed to output
    private final int infinites;

    public DoubleGenerator(double[] arr, int infinites, BlockingDoubleQueue output) {
      this.arr = arr;
      this.output = output;
      this.infinites = infinites;
    }

    public void run() {
      for (int i = 0; i < arr.length; i++) // The numbers to sort
        output.put(arr[i]);
      for (int i = 0; i < infinites; i++) // Infinite numbers for wash-out
        output.put(Double.POSITIVE_INFINITY);
    }
  }

  static class SortedChecker implements Runnable {
    // If DEBUG is true, print the first 100 numbers received
    private final static boolean DEBUG = false;
    private final BlockingDoubleQueue input;
    private final int itemCount; // the number of items to check

    public SortedChecker(int itemCount, BlockingDoubleQueue input) {
      this.itemCount = itemCount;
      this.input = input;
    }

    public void run() {
      int consumed = 0;
      double last = Double.NEGATIVE_INFINITY;
      while (consumed++ < itemCount) {
        double p = input.take();
        if (DEBUG && consumed <= 100)
          System.out.print(p + " ");
        if (p <= last)
          System.out.printf("Elements out of order: %g before %g%n", last, p);
        last = p;
      }
      if (DEBUG)
        System.out.println();
    }
  }

  // --- Benchmarking infrastructure ---

  // NB: Modified to show milliseconds instead of nanoseconds

  public static double Mark7(String msg, IntToDoubleFunction f) {
    int n = 10, count = 1, totalCount = 0;
    double dummy = 0.0, runningTime = 0.0, st = 0.0, sst = 0.0;
    do {
      count *= 2;
      st = sst = 0.0;
      for (int j = 0; j < n; j++) {
        Timer t = new Timer();
        for (int i = 0; i < count; i++)
          dummy += f.applyAsDouble(i);
        runningTime = t.check();
        double time = runningTime * 1e3 / count;
        st += time;
        sst += time * time;
        totalCount += count;
      }
    } while (runningTime < 0.25 && count < Integer.MAX_VALUE / 2);
    double mean = st / n, sdev = Math.sqrt((sst - mean * mean * n) / (n - 1));
    System.out.printf("%-25s %15.1f ms %10.2f %10d%n", msg, mean, sdev, count);
    return dummy / totalCount;
  }

  public static void SystemInfo() {
    System.out.printf("# OS:   %s; %s; %s%n", System.getProperty("os.name"), System.getProperty("os.version"),
        System.getProperty("os.arch"));
    System.out.printf("# JVM:  %s; %s%n", System.getProperty("java.vendor"), System.getProperty("java.version"));
    // The processor identifier works only on MS Windows:
    System.out.printf("# CPU:  %s; %d \"cores\"%n", System.getenv("PROCESSOR_IDENTIFIER"),
        Runtime.getRuntime().availableProcessors());
    java.util.Date now = new java.util.Date();
    System.out.printf("# Date: %s%n", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(now));
  }

  // Crude wall clock timing utility, measuring time in seconds

  static class Timer {
    private long start, spent = 0;

    public Timer() {
      play();
    }

    public double check() {
      return (System.nanoTime() - start + spent) / 1e9;
    }

    public void pause() {
      spent += System.nanoTime() - start;
    }

    public void play() {
      start = System.nanoTime();
    }
  }
}

// ----------------------------------------------------------------------

// Queue interface

interface BlockingDoubleQueue {
  double take();

  void put(double item);
}

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

// class WaitFreeQueue<T> {
// volatile int head = 0, tail = 0;
// T[] items;

// public WaitFreeQueue(int capacity) {
// items = (T[]) new Object[capacity];
// }

// public void enq(T x) throws FullException {
// if (tail - head == items.length)
// throw new FullException();
// items[tail % items.length] = x;
// tail++;
// }

// public T deq() throws EmptyException {
// if (tail - head == 0)
// throw new EmptyException();
// T x = items[head % items.length];
// head++;
// return x;
// }
// }

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

  public void printQueue() {
    System.out.println("Head: " + head + " Tail: " + tail + " Elements: ");
    for (int i = 0; i < items.length; i++) {
      System.out.print(items[i] + ", ");
    }
    System.out.println("\n");
  }
}

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

  public String toString() {
    return arrayBlockingQueue.toString();
  }
}

// The queue implementations

// TO DO

// ----------------------------------------------------------------------

class DoubleArray {
  public static double[] randomPermutation(int n) {
    double[] arr = fillDoubleArray(n);
    shuffle(arr);
    return arr;
  }

  private static double[] fillDoubleArray(int n) {
    double[] arr = new double[n];
    for (int i = 0; i < n; i++)
      arr[i] = i + 0.1;
    return arr;
  }

  private static final java.util.Random rnd = new java.util.Random();

  private static void shuffle(double[] arr) {
    for (int i = arr.length - 1; i > 0; i--)
      swap(arr, i, rnd.nextInt(i + 1));
  }

  // Swap arr[s] and arr[t]
  private static void swap(double[] arr, int s, int t) {
    double tmp = arr[s];
    arr[s] = arr[t];
    arr[t] = tmp;
  }

  // Minheap operations for parallel sort pipelines.
  // Minheap invariant:
  // If heap[0..k-1] is a minheap, then heap[(i-1)/2] <= heap[i] for
  // all indexes i=1..k-1. Thus heap[0] is the smallest element.

  // Although stored in an array, the heap can be considered a tree
  // where each element heap[i] is a node and heap[(i-1)/2] is its
  // parent. Then heap[0] is the tree's root and a node heap[i] has
  // children heap[2*i+1] and heap[2*i+2] if these are in the heap.

  // In heap[0..k], move node heap[i] downwards by swapping it with
  // its smallest child until the heap invariant is reestablished.

  public static void minheapSiftdown(double[] heap, int i, int k) {
    int child = 2 * i + 1;
    if (child <= k) {
      if (child + 1 <= k && heap[child] > heap[child + 1])
        child++;
      if (heap[i] > heap[child]) {
        swap(heap, i, child);
        minheapSiftdown(heap, child, k);
      }
    }
  }

  // In heap[0..k], move node heap[i] upwards by swapping with its
  // parent until the heap invariant is reestablished.
  public static void minheapSiftup(double[] heap, int i, int k) {
    if (0 < i) {
      int parent = (i - 1) / 2;
      if (heap[i] < heap[parent]) {
        swap(heap, i, parent);
        minheapSiftup(heap, parent, k);
      }
    }
  }
}
