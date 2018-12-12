// For PCPP exam January 2015
// sestoft@itu.dk * 2015-01-03 with post-exam updates 2015-01-09

// Several versions of sequential and parallel quicksort: 
// A: sequential recursive
// B: sequential using work a deque as a stack

// To do by students:
// C: single-queue multi-threaded with shared lock-based queue
// D: multi-queue multi-threaded with thread-local lock-based queues and stealing
// E: as D but with thread-local lock-free queues and stealing

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class Quicksorts {
  final static int size = 1_000_000; // Number of integers to sort

  public static void main(String[] args) {
    sequentialRecursive();
    singleQueueSingleThread();
    //    singleQueueMultiThread(8);
    //    multiQueueMultiThread(8);
    //    multiQueueMultiThreadCL(8);
  }

  // ----------------------------------------------------------------------
  // Version A: Standard sequential quicksort using recursion

  private static void sequentialRecursive() {
    int[] arr = IntArrayUtil.randomIntArray(size);
    qsort(arr, 0, arr.length-1);
    System.out.println(IntArrayUtil.isSorted(arr));
  }

  // Sort arr[a..b] endpoints inclusive
  private static void qsort(int[] arr, int a, int b) {
    if (a < b) { 
      int i = a, j = b;
      int x = arr[(i+j) / 2];         
      do {                            
        while (arr[i] < x) i++;       
        while (arr[j] > x) j--;       
        if (i <= j) {
          swap(arr, i, j);
          i++; j--;
        }                             
      } while (i <= j); 
      qsort(arr, a, j); 
      qsort(arr, i, b); 
    }
  }

  // Swap arr[s] and arr[t]
  private static void swap(int[] arr, int s, int t) {
    int tmp = arr[s];  arr[s] = arr[t];  arr[t] = tmp;
  }

  // ----------------------------------------------------------------------
  // Version B: Single-queue single-thread setup; sequential quicksort using queue

  private static void singleQueueSingleThread() {
    SimpleDeque<SortTask> queue = new SimpleDeque<SortTask>(100000);
    int[] arr = IntArrayUtil.randomIntArray(size);
    queue.push(new SortTask(arr, 0, arr.length-1));
    sqstWorker(queue);
    System.out.println(IntArrayUtil.isSorted(arr));
  }

  private static void sqstWorker(Deque<SortTask> queue) {
    SortTask task;
    while (null != (task = queue.pop())) {
      final int[] arr = task.arr;
      final int a = task.a, b = task.b;
      if (a < b) { 
        int i = a, j = b;
        int x = arr[(i+j) / 2];         
        do {                            
          while (arr[i] < x) i++;       
          while (arr[j] > x) j--;       
          if (i <= j) {
            swap(arr, i, j);
            i++; j--;
          }                             
        } while (i <= j); 
        queue.push(new SortTask(arr, a, j)); 
        queue.push(new SortTask(arr, i, b));               
      }
    }
  }

  // ---------------------------------------------------------------------- 
  // Version C: Single-queue multi-thread setup 

  private static void singleQueueMultiThread(final int threadCount) {
    int[] arr = IntArrayUtil.randomIntArray(size);
    // To do: ... create queue, then call sqmtWorkers(queue, threadCount)
    System.out.println(IntArrayUtil.isSorted(arr));
  }

  private static void sqmtWorkers(Deque<SortTask> queue, int threadCount) {
    // To do: ... create and start threads and so on ...
  }

  // Tries to get a sorting task.  If task queue is empty but some
  // tasks are not yet processed, yield and then try again.

  private static SortTask getTask(final Deque<SortTask> queue, LongAdder ongoing) {
    SortTask task;
    while (null == (task = queue.pop())) {
      if (ongoing.longValue() > 0) 
        Thread.yield();
      else 
        return null;
    }
    return task;
  }


  // ----------------------------------------------------------------------
  // Version D: Multi-queue multi-thread setup, thread-local queues

  private static void multiQueueMultiThread(final int threadCount) {
    int[] arr = IntArrayUtil.randomIntArray(size);
    // To do: ... create queues and so on, then call mqmtWorkers(queues, threadCount)
    System.out.println(IntArrayUtil.isSorted(arr));
  }

  // Version E: Multi-queue multi-thread setup, thread-local queues

  private static void multiQueueMultiThreadCL(final int threadCount) {
    int[] arr = IntArrayUtil.randomIntArray(size);
    // To do: ... create queues and so on, then call mqmtWorkers(queues, threadCount)
    System.out.println(IntArrayUtil.isSorted(arr));
  }

  private static void mqmtWorkers(Deque<SortTask>[] queues, int threadCount) {
    // To do: ... create and start threads and so on ...
  }

  // Tries to get a sorting task.  If task queue is empty, repeatedly
  // try to steal, cyclically, from other threads and if that fails,
  // yield and then try again, while some sort tasks are not processed.

  private static SortTask getTask(final int myNumber, final Deque<SortTask>[] queues, 
                                  LongAdder ongoing) {
    final int threadCount = queues.length;
    SortTask task = queues[myNumber].pop();
    if (null != task) 
      return task;
    else {
      do {
        // To do here: ... try to steal from other tasks' queues ...
        Thread.yield();
      } while (ongoing.longValue() > 0);
      return null;
    }
  }
}

// ----------------------------------------------------------------------
// SortTask class, Deque<T> interface, SimpleDeque<T> 

// Represents the task of sorting arr[a..b]
class SortTask {
  public final int[] arr;
  public final int a, b;

  public SortTask(int[] arr, int a, int b) {
    this.arr = arr; 
    this.a = a;
    this.b = b;
  }
}

interface Deque<T> {
  void push(T item);    // at bottom
  T pop();              // from bottom
  T steal();            // from top
}

class SimpleDeque<T> implements Deque<T> {
  // The queue's items are in items[top%S...(bottom-1)%S], where S ==
  // items.length; items[bottom%S] is where the next push will happen;
  // items[(bottom-1)%S] is where the next pop will happen;
  // items[top%S] is where the next steal will happen; the queue is
  // empty if top == bottom, non-empty if top < bottom, and full if
  // bottom - top == items.length.  The top field can only increase.
  private long bottom = 0, top = 0;
  private final T[] items;

  public SimpleDeque(int size) {
    this.items = makeArray(size);
  }

  @SuppressWarnings("unchecked") 
  private static <T> T[] makeArray(int size) {
    // Java's @$#@?!! type system requires this unsafe cast    
    return (T[])new Object[size];
  }

  private static int index(long i, int n) {
    return (int)(i % (long)n);
  }

  public void push(T item) { // at bottom
    final long size = bottom - top;
    if (size == items.length) 
      throw new RuntimeException("queue overflow");
    items[index(bottom++, items.length)] = item;
  }

  public T pop() { // from bottom
    final long afterSize = bottom - 1 - top;
    if (afterSize < 0) 
      return null;
    else
      return items[index(--bottom, items.length)];
  }

  public T steal() { // from top
    final long size = bottom - top;
    if (size <= 0) 
      return null;
    else
      return items[index(top++, items.length)];
  }
}

// ----------------------------------------------------------------------

// A lock-free queue simplified from Chase and Lev: Dynamic circular
// work-stealing queue, SPAA 2005.  We simplify it by not reallocating
// the array; hence this queue may overflow.  This is close in spirit
// to the original ABP work-stealing queue (Arora, Blumofe, Plaxton:
// Thread scheduling for multiprogrammed multiprocessors, 2000,
// section 3) but in that paper an "age" tag needs to be added to the
// top pointer to avoid the ABA problem (see ABP section 3.3).  This
// is not necessary in the Chase-Lev dequeue design, where the top
// index never assumes the same value twice.

// PSEUDOCODE for ChaseLevDeque class:

/* 
class ChaseLevDeque<T> implements Deque<T> {
  long bottom = 0, top = 0;
  T[] items;

  public ChaseLevDeque(int size) {
    this.items = makeArray(size);
  }

  @SuppressWarnings("unchecked") 
  private static <T> T[] makeArray(int size) {
    // Java's @$#@?!! type system requires this unsafe cast    
    return (T[])new Object[size];
  }

  private static int index(long i, int n) {
    return (int)(i % (long)n);
  }

  public void push(T item) { // at bottom
    final long b = bottom, t = top, size = b - t;
    if (size == items.length) 
      throw new RuntimeException("queue overflow");
    items[index(b, items.length)] = item;
    bottom = b+1;
  }

  public T pop() { // from bottom
    final long b = bottom - 1;
    bottom = b;
    final long t = top, afterSize = b - t;
    if (afterSize < 0) { // empty before call
      bottom = t;
      return null;
    } else {
      T result = items[index(b, items.length)];
      if (afterSize > 0) // non-empty after call
        return result;
      else {		// became empty, update both top and bottom
        if (!CAS(top, t, t+1)) // somebody stole result
          result = null;
        bottom = t+1;
        return result;
      }
    }
  }

  public T steal() { // from top
    final long t = top, b = bottom, size = b - t;
    if (size <= 0)
      return null;
    else {
      T result = items[index(t, items.length)];
      if (CAS(top, t, t+1))
        return result;
      else 
        return null;
    }
  }
}

*/

// ----------------------------------------------------------------------

class IntArrayUtil {
  public static int[] randomIntArray(final int n) {
    int[] arr = new int[n];
    for (int i = 0; i < n; i++)
      arr[i] = (int) (Math.random() * n * 2);
    return arr;
  }

  public static void printout(final int[] arr, final int n) {
    for (int i=0; i < n; i++)
      System.out.print(arr[i] + " ");
    System.out.println("");
  }

  public static boolean isSorted(final int[] arr) {
    for (int i=1; i<arr.length; i++)
      if (arr[i-1] > arr[i])
        return false;
    return true;
  }
}