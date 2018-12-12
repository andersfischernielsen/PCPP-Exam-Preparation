import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class TestLocking2 {
  public static void main(String[] args) {
    int count = 1_000;
    // DoubleArrayList dal1 = new DoubleArrayList();
    // DoubleArrayList dal2 = new DoubleArrayList();
    // DoubleArrayList dal3 = new DoubleArrayList();
    Thread t1 = new Thread(() -> {
      for (int i = 1; i <= count; i++) {
        DoubleArrayList dal = new DoubleArrayList();
        dal.add(i);
      }
    });
    Thread t2 = new Thread(() -> {
      for (int i = 1; i <= count; i++) {
        DoubleArrayList dal = new DoubleArrayList();
        dal.add(i);

      }
    });
    t1.start();
    t2.start();
    try {
      t1.join();
      t2.join();
    } catch (InterruptedException exn) {
    }
    System.out.printf("Total size = %d%n", DoubleArrayList.totalSize());
    System.out.printf("All lists = %s%n", DoubleArrayList.allLists());
    System.out.printf("All lists size= %s%n", DoubleArrayList.allLists().size());

    int sum = 0;
    for (DoubleArrayList list : DoubleArrayList.allLists()) {
      sum += list.get(0);
    }

    System.out.printf("Sum of all lists = %s%n", sum); // should be (1000^2+1000)/2 = 500.500 * 2 = 1.001.000

  }
}

// Expandable array list of doubles, also keeping track of all such
// array lists and their total element count.

class DoubleArrayList {
  private static AtomicInteger totalSize = new AtomicInteger();
  private static HashSet<DoubleArrayList> allLists = new HashSet<>();

  // Invariant: 0 <= size <= items.length
  private double[] items = new double[2];
  private int size = 0;

  public DoubleArrayList() {
    synchronized (allLists) {
      allLists.add(this);
    }
  }

  // Number of items in the double list
  public int size() {
    return size;
  }

  // Return item number i, if any
  public double get(int i) {
    if (0 <= i && i < size)
      return items[i];
    else
      throw new IndexOutOfBoundsException(String.valueOf(i));
  }

  // Add item x to end of list
  public boolean add(double x) {
    if (size == items.length) {
      double[] newItems = new double[items.length * 2];
      for (int i = 0; i < items.length; i++)
        newItems[i] = items[i];
      items = newItems;
    }
    items[size] = x;
    size++;
    totalSize.getAndIncrement();
    return true;
  }

  // Replace item number i, if any, with x
  public double set(int i, double x) {
    if (0 <= i && i < size) {
      double old = items[i];
      items[i] = x;
      return old;
    } else
      throw new IndexOutOfBoundsException(String.valueOf(i));
  }

  // The double list formatted as eg "[3.2, 4.7]"
  public String toString() {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < size; i++)
      sb.append(i > 0 ? ", " : "").append(items[i]);
    return sb.append("]").toString();
  }

  public static int totalSize() {
    return totalSize.get();
  }

  public static HashSet<DoubleArrayList> allLists() {
    return allLists;
  }
}
