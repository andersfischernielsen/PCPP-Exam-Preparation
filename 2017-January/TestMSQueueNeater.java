// sestoft@itu.dk * 2016-11-18, 2017-01-08

import java.util.concurrent.atomic.AtomicReference;

public class TestMSQueueNeater extends Tests {
  public static void main(String[] args) {
  }
}

class Tests {
  public static void assertEquals(int x, int y) throws Exception {
    if (x != y) 
      throw new Exception(String.format("ERROR: %d not equal to %d%n", x, y));
  }

  public static void assertTrue(boolean b) throws Exception {
    if (!b) 
      throw new Exception(String.format("ERROR: assertTrue"));
  }
}

interface UnboundedQueue<T> {
  void enqueue(T item);
  T dequeue();
}

// Unbounded non-blocking list-based lock-free queue by Michael and
// Scott 1996.  This version inspired by suggestions from Niels
// Abildgaard Roesen.

class MSQueueNeater<T> implements UnboundedQueue<T> {
  private final AtomicReference<Node<T>> head, tail;

  public MSQueueNeater() {
    Node<T> dummy = new Node<T>(null, null);
    head = new AtomicReference<Node<T>>(dummy);
    tail = new AtomicReference<Node<T>>(dummy);
  }

  public void enqueue(T item) { // at tail
    Node<T> node = new Node<T>(item, null);
    while (true) {
      final Node<T> last = tail.get(), next = last.next.get();
      if (next != null)
	tail.compareAndSet(last, next);
      else if (last.next.compareAndSet(null, node)) {
	tail.compareAndSet(last, node);
	return;
      }
    }
  }

  public T dequeue() { // from head
    while (true) {
      final Node<T> first = head.get(), last = tail.get(), next = first.next.get();
      if (next == null)
	return null;
      else if (first == last) 
	tail.compareAndSet(last, next);
      else if (head.compareAndSet(first, next))
	return next.item;
    }
  }

  private static class Node<T> {
    final T item;
    final AtomicReference<Node<T>> next;

    public Node(T item, Node<T> next) {
      this.item = item;
      this.next = new AtomicReference<Node<T>>(next);
    }
  }
}
