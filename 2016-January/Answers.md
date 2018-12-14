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
### 4.1
```java
static class SortingStage implements Runnable {
    BlockingDoubleQueue inputQueue;
    BlockingDoubleQueue outputQueue;

    double[] heap;

    int heapSize; //No. of elements currently stored in the heap
    int itemCount; //No of elements to be produced as output before it terminates

    public SortingStage(BlockingDoubleQueue inputQueue, BlockingDoubleQueue outputQueue, int size, int itemCount){
      heap = new double[size]; 
      this.inputQueue = inputQueue;
      this.outputQueue = outputQueue;            
      this.itemCount = itemCount;//Change this to whatever itemCount/Boundary needed.
      heapSize = 0;
    }    
    
    public void run() { 
      while (itemCount > 0) { //Dies at itemcount = 2, all in inputQueue is null at that point
        double x = inputQueue.take(); //... get next number from input ...
        if (heapSize < heap.length) { // heap not full, put x into it
        heap[heapSize++] = x;
        DoubleArray.minheapSiftup(heap, heapSize-1, heapSize-1);
        } else if (x <= heap[0]) { //x is less equal to smallest number, forward x to next stage
          //output x
          outputQueue.put(x);
          itemCount--;
        } else {    //x is bigger than least number forward least, replace with x
          double least = heap[0];
          heap[0] = x;
          DoubleArray.minheapSiftdown(heap, 0, heapSize-1);
          outputQueue.put(least);
          itemCount--;
        }
      }
    }
  }
```

### Question 4.2
```java
private static void sortPipeline(double[] arr, int P, BlockingDoubleQueue[] queues) {
  Thread[] threads = new Thread[P+2];
    for (int i = 0; i < threads.length; i++) {
      if(i == 0){ //Create DoubleGenerator, set output queue to first queue
        Runnable dg = new DoubleGenerator(arr, arr.length, queues[0]);
        threads[0] = new Thread(dg); 
      }
      else if(i < threads.length-1){ //for all but the last thread, create a sorting stage and set it's input queue to the queue before it and output to current index
        int size = arr.length/P;
        // int itemCount = arr.length + (P-(i+1))*size;
        int itemCount = arr.length + (P-(i))*size;
        Runnable ss = new SortingStage(queues[i-1], queues[i], arr.length/P, itemCount);
        threads[i] = new Thread(ss);        
      }
      else{ //This is the last thread. Create a SortedChecker, and set it's input queue to the last index in queues
        Runnable sc = new SortedChecker(arr.length, queues[queues.length-1]); 
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
  } 
``` 

## Question 5
### Question 5.1
```java
class WrappedArrayDoubleQueue implements BlockingDoubleQueue {

  ArrayBlockingQueue<Double> arrayBlockingQueue;

  public WrappedArrayDoubleQueue(){
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
    }
  }
}
```

### Question 5.2
```
# OS:   Mac OS X; 10.14; x86_64
# JVM:  Oracle Corporation; 1.8.0_60
# CPU:  null; 4 "cores"
# Date: 2018-12-13T15:14:25+0100
0.1 1.1 2.1 3.1 4.1 5.1 6.1 7.1 8.1 9.1 10.1 11.1 12.1 13.1 14.1 15.1 16.1 17.1 18.1 19.1 20.1 21.1 22.1 23.1 24.1 25.1 26.1 27.1 28.1 29.1 30.1 31.1 32.1 33.1 34.1 35.1 36.1 37.1 38.1 39.1
```

### Question 5.3
```
# OS:   Mac OS X; 10.14; x86_64
# JVM:  Oracle Corporation; 1.8.0_60
# CPU:  null; 4 "cores"
# Date: 2018-12-13T15:27:50+0100
Finished running sortPipeline           172,1 ms      33,36          2
```

I'm not really sure how to discuss these results. 
The computation finishes in 172,1 ms with a standard deviation of 33,36, which seems pretty good to me. 

## Question 6
### Question 6.1
```java
class BlockingNDoubleQueue implements BlockingDoubleQueue {
  double[] queue;
  int head, tail, elementsInQueue;


  public BlockingNDoubleQueue() {
    this.queue = new double[50];        
    head = 0; tail = 0; elementsInQueue=0;
  }

  public synchronized void put (double item){
    while(elementsInQueue == queue.length){ 
      try {
        wait();
      } catch (Exception e) {
        //TODO: handle exception
      }
    }
    if(tail == queue.length && head != 0){  //Zero based index so this means tail has now surpassed final index of array
      reallocateQueue();
    }
    queue[tail] = item;
    elementsInQueue ++;
    tail++;
    notifyAll();
    
  }

  public synchronized double take(){
    while(elementsInQueue == 0){
      try {
        wait();
      } catch (Exception e) {
        //TODO: handle exception
      }
    }    
    elementsInQueue--;
    notifyAll();    
    //Should return element at index head, then increment head by 1
    return queue[head++]; 
  }

  void reallocateQueue(){
    //Should reallocate wait for queue to not be full?
    //Don't think we can use i = head like this. It basically says queue[head-head] everytime == 0
    for (int i = head; i < queue.length; i++) {
      queue[i-head] = queue[i];
    }
    tail = queue.length - head;
    head = 0;
  }
```

### Question 6.2
The implementation is thread safe because a thread will allways wait on a put operation if the queue is full, 
and wait on a take operation if the queue is empty. Thus we do not risk getting array out of bounds exceptions. 
Furthermore, because both put and take make use of the synchronized keyword, no two threads will perform a put or take operation at the 
same time. This is not very good for performance, but ensures thread safety.
If a thread t1 tries to put into a full queue, it will wait.
If another thread t2 then takes an element from the queue, it will notify t1, which will continue to check if the queue is full.
If this is not the case, t1 will finish its' put operation.


### Question 6.3
Finished running sortPipeline           713,6 ms      63,04          2




## Question 11

