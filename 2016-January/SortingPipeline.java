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

// sestoft@itu.dk * 2016-01-10

import java.util.stream.DoubleStream;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.io.Serializable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.IntToDoubleFunction;
import java.util.concurrent.atomic.AtomicReference;

import org.multiverse.api.Txn;
import org.multiverse.api.references.*;
import org.multiverse.api.callables.*;
import org.multiverse.api.GlobalStmInstance;
import org.multiverse.api.StmUtils;
import static org.multiverse.api.StmUtils.*;

import java.util.concurrent.Callable;

public class SortingPipeline {
  public static void main(String[] args) {
    SystemInfo();
    final int count = 100_000, P = 4;
    // final int count = 40, P = 4;

    final double[] arr = DoubleArray.randomPermutation(count);

    // Test BlockingDoubleQueue
    // ----------------------------------------------------------------------------
    // TO DO: Create and run pipeline to sort numbers from arr
    // BlockingDoubleQueue[] queues = new BlockingDoubleQueue[P+1];
    // for (int i = 0; i < queues.length; i++) {
    // queues[i] = new WrappedArrayDoubleQueue();
    // }
    // Mark7("Finished running sortPipeline", f -> sortPipeline(arr, P, queues));

    // Test BlockingNDoubleQueue
    // ----------------------------------------------------------------------------
    // BlockingDoubleQueue[] queues = new BlockingNDoubleQueue[P + 1];
    // for (int i = 0; i < queues.length; i++) {
    // queues[i] = new BlockingNDoubleQueue();
    // }
    // Mark7("Finished running sortPipeline", f -> sortPipeline(arr, P, queues));

    // // Test LockingQueue
    // //
    // ----------------------------------------------------------------------------
    // BlockingDoubleQueue[] queues = new UnboundedBlockingQueue[P + 1];
    // for (int i = 0; i < queues.length; i++) {
    // queues[i] = new UnboundedBlockingQueue();
    // }
    // Mark7("Finished running sortPipeline", f -> sortPipeline(arr, P, queues));

    // // Test NoLockNDoubleQueue
    // //
    // ----------------------------------------------------------------------------
    // BlockingDoubleQueue[] queues = new NoLockNDoubleQueue[P + 1];
    // for (int i = 0; i < queues.length; i++) {
    // queues[i] = new NoLockNDoubleQueue();
    // }
    // Mark7("Finished running sortPipeline", f -> sortPipeline(arr, P, queues));

    // // Test MSUnboundedDoubleQueue
    // //
    // ----------------------------------------------------------------------------
    // BlockingDoubleQueue[] queues = new MSUnboundedDoubleQueue[P + 1];
    // for (int i = 0; i < queues.length; i++) {
    // queues[i] = new MSUnboundedDoubleQueue();
    // }
    // // sortPipeline(arr, P, queues);
    // Mark7("Finished running sortPipeline", f -> sortPipeline(arr, P, queues));

    // Test StmBlockingNDoubleQueue
    // ----------------------------------------------------------------------------
    BlockingDoubleQueue[] queues = new StmBlockingNDoubleQueue[P + 1];
    for (int i = 0; i < queues.length; i++) {
      queues[i] = new StmBlockingNDoubleQueue();
    }
    // sortPipeline(arr, P, queues);
    Mark7("Finished running sortPipeline", f -> sortPipeline(arr, P, queues));

  }

  private static double sortPipeline(double[] arr, int P, BlockingDoubleQueue[] queues) {
    // private static void sortPipeline(double[] arr, int P, BlockingDoubleQueue[]
    // queues) {
    // Sequential implementation

    // //Set up doublegenerator and connect it to first queue
    // int infinites = arr.length;

    // Thread t1 = new
    // DoubleGenerator dg = new DoubleGenerator(arr, infinites, queues[0]);

    // dg.run();

    // //Create P sortingstages and connect queues
    // SortingStage[] sortingStages = new SortingStage[P];
    // for (int i = 0; i < queues.length-1; i++) {
    // int size = arr.length/P;
    // int itemCount = arr.length + (P-(i+1))*size;
    // sortingStages[i] = new SortingStage(queues[i], queues[i+1], size, itemCount);
    // sortingStages[i].run();
    // }

    // //Set up SortedChecker and connect it
    // BlockingDoubleQueue inputQueueSc = queues[queues.length-1];
    // int scItemCount = arr.length;
    // SortedChecker sc = new SortedChecker(scItemCount, inputQueueSc);
    // sc.run();

    Thread[] threads = new Thread[P + 2];
    for (int i = 0; i < threads.length; i++) {
      if (i == 0) { // Create DoubleGenerator, set output queue to first queue
        Runnable dg = new DoubleGenerator(arr, arr.length, queues[0]);
        threads[0] = new Thread(dg);
      } else if (i < threads.length - 1) { // for all but the last thread, create a sorting stage and set it's input
                                           // queue to the queue before it and output to current index
        int size = arr.length / P;
        // int itemCount = arr.length + (P-(i+1))*size;
        int itemCount = arr.length + (P - (i)) * size;
        Runnable ss = new SortingStage(queues[i - 1], queues[i], arr.length / P, itemCount);
        threads[i] = new Thread(ss);
      } else { // This is the last thread. Create a SortedChecker, and set it's input queue to
               // the last index in queues
        Runnable sc = new SortedChecker(arr.length, queues[queues.length - 1]);
        threads[i] = new Thread(sc);
      }
    }

    for (int i = 0; i < threads.length; i++) {
      threads[i].start();
    }

    for (int i = 0; i < threads.length; i++) {
      try {
        threads[i].join();
      } catch (Exception e) {
      }
    }
    return 0.0;
  }

  static class SortingStage implements Runnable {
    BlockingDoubleQueue inputQueue;
    BlockingDoubleQueue outputQueue;

    double[] heap;

    int heapSize; // No. of elements currently stored in the heap
    int itemCount; // No of elements to be produced as output before it terminates

    public SortingStage(BlockingDoubleQueue inputQueue, BlockingDoubleQueue outputQueue, int size, int itemCount) {
      heap = new double[size];
      this.inputQueue = inputQueue;
      this.outputQueue = outputQueue;
      this.itemCount = itemCount;// Change this to whatever itemCount/Boundary needed.
      heapSize = 0;
    }

    public void run() {
      while (itemCount > 0) { // Dies at itemcount = 2, all in inputQueue is null at that point
        double x = inputQueue.take(); // ... get next number from input ...
        if (heapSize < heap.length) { // heap not full, put x into it
          heap[heapSize++] = x;
          DoubleArray.minheapSiftup(heap, heapSize - 1, heapSize - 1);
        } else if (x <= heap[0]) { // x is less equal to smallest number, forward x to next stage
          // output x
          outputQueue.put(x);
          itemCount--;
        } else { // x is bigger than least number forward least, replace with x
          double least = heap[0];
          heap[0] = x;
          DoubleArray.minheapSiftdown(heap, 0, heapSize - 1);
          outputQueue.put(least);
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
    // private final static boolean DEBUG = true;

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

// The queue implementations

class WrappedArrayDoubleQueue implements BlockingDoubleQueue {

  ArrayBlockingQueue<Double> arrayBlockingQueue;

  public WrappedArrayDoubleQueue() {
    this.arrayBlockingQueue = new ArrayBlockingQueue<>(50);
  }

  public double take() {
    try {
      return arrayBlockingQueue.take();
    } catch (Exception e) {
      return -1;
    }
  }

  public void put(double item) {
    try {
      arrayBlockingQueue.put((item));
    } catch (Exception e) {
      // TODO: handle exception
    }
  }
}

class BlockingNDoubleQueue implements BlockingDoubleQueue {
  double[] queue;
  int head, tail, elementsInQueue;

  public BlockingNDoubleQueue() {
    this.queue = new double[50];
    head = 0;
    tail = 0;
    elementsInQueue = 0;
  }

  public synchronized void put(double item) {
    while (elementsInQueue == queue.length) {
      try {
        wait();
      } catch (Exception e) {
        // TODO: handle exception
      }
    }
    if (tail == queue.length && head != 0) { // Zero based index so this means tail has now surpassed final index of
                                             // array
      reallocateQueue();
    }
    queue[tail] = item;
    elementsInQueue++;
    tail++;
    notifyAll();
  }

  public synchronized double take() {
    while (elementsInQueue == 0) {
      try {
        wait();
      } catch (Exception e) {
        // TODO: handle exception
      }
    }
    elementsInQueue--;
    notifyAll();
    // Should return element at index head, then increment head by 1
    return queue[head++];
  }

  void reallocateQueue() {
    for (int i = head; i < queue.length; i++) {
      queue[i - head] = queue[i];
    }
    tail = queue.length - head;
    head = 0;
  }
}

// ------------------------------------------------------------
// Unbounded lock-based queue with sentinel (dummy) node

class UnboundedBlockingQueue implements BlockingDoubleQueue {
  // Invariants:
  // The node referred by tail is reachable from head.
  // If non-empty then head != tail,
  // and tail points to last item, and head.next to first item.
  // If empty then head == tail.

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
    head = tail = new Node(-1.0, null);
  }

  public synchronized void put(double item) { // at tail
    Node node = new Node(item, null);
    tail.next = node;
    tail = node;
    notifyAll();
  }

  public synchronized double take() { // from head
    while (head.next == null) {
      try {
        wait();
      } catch (Exception e) {
        // TODO: handle exception
      }
    }
    Node first = head;
    head = first.next;
    return head.item;
  }
}

class NoLockNDoubleQueue implements BlockingDoubleQueue {
  volatile int head = 0, tail = 0;
  final double[] items;

  public NoLockNDoubleQueue() {
    items = (double[]) new double[50];
  }

  public void put(double x) {
    while (tail - head == items.length) {
    }
    items[tail % items.length] = x;
    tail++;
  }

  public double take() {
    while (tail - head == 0) {
    }
    double x = items[head % items.length];
    head++;
    return x;
  }

}

class MSUnboundedDoubleQueue implements BlockingDoubleQueue {
  private final AtomicReference<Node<Double>> head, tail;

  public MSUnboundedDoubleQueue() {
    Node<Double> dummy = new Node<Double>(null, null);
    head = new AtomicReference<Node<Double>>(dummy);
    tail = new AtomicReference<Node<Double>>(dummy);
  }

  public void put(double item) { // at tail
    Node<Double> node = new Node<Double>(item, null);
    while (true) {
      Node<Double> last = tail.get(), next = last.next.get();
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
    }
  }

  public double take() { // from head
    while (true) {
      Node<Double> first = head.get(), last = tail.get(), next = first.next.get();

      if (first == last) {

        if (next != null) {
          tail.compareAndSet(last, next);
        }

      } else {
        double result = next.item;
        if (head.compareAndSet(first, next))
          return result;
      }

    }
  }

  private static class Node<Double> {
    final Double item;
    final AtomicReference<Node<Double>> next;

    public Node(Double item, Node<Double> next) {
      this.item = item;
      this.next = new AtomicReference<Node<Double>>(next);
    }
  }
}

class StmBlockingNDoubleQueue implements BlockingDoubleQueue {
  private final TxnInteger availableItems, availableSpaces;
  private final TxnRef<Double>[] items;
  private final TxnInteger head, tail;
  private final int capacity;

  public StmBlockingNDoubleQueue() {
    capacity = 50;
    this.availableItems = newTxnInteger(0);
    this.availableSpaces = newTxnInteger(capacity);
    this.items = makeArray(capacity);
    for (int i = 0; i < capacity; i++)
      this.items[i] = StmUtils.<Double>newTxnRef();
    this.head = newTxnInteger(0);
    this.tail = newTxnInteger(0);
  }

  @SuppressWarnings("unchecked")
  private static TxnRef<Double>[] makeArray(int capacity) {
    // Java's @$#@?!! type system requires this unsafe cast
    return (TxnRef<Double>[]) new TxnRef[capacity];
  }

  public boolean isEmpty() {
    return atomic(() -> availableItems.get() == 0);
  }

  public boolean isFull() {
    return atomic(() -> availableSpaces.get() == 0);
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
}

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
