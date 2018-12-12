// For week 12
// sestoft@itu.dk * 2014-11-20

// Some implementations of parallel union-find.  See Berman: Multicore
// programming in the face of metamorphosis: union-find as an example,
// MSc thesis, Tel-Aviv University 2010; Anderson and Woll: Wait-free
// parallel algorithms for the union-find problem, 23rd ACM STOC,
// 1991; and Florian Biermann: Connected set filtering on shared
// memory multiprocessors, MSc thesis, IT University of Copenhagen,
// June 2014.
 
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

import java.util.Arrays;
import java.util.Collections;

public class TestUnionFind {
  public static void main(String[] args) throws Exception {
    final int itemCount = 10_000;
    {
      UnionFindTest test = new UnionFindTest();
      test.sequential(new CoarseUnionFind(5));
      test.concurrent(itemCount, new CoarseUnionFind(itemCount));
    }
    {
      UnionFindTest test = new UnionFindTest();
      test.sequential(new FineUnionFind(5));
      test.concurrent(itemCount, new FineUnionFind(itemCount));
    }
    {
      UnionFindTest test = new UnionFindTest();
      test.sequential(new WaitFreeUnionFind(5));
      test.concurrent(itemCount, new WaitFreeUnionFind(itemCount));
    }
  }
}

interface UnionFind {
  int find(int x);
  void union(int x, int y);
  boolean sameSet(int x, int y);
}

// Test of union-find data structures, adapted from Florian Biermann's
// MSc thesis, ITU 2014

class UnionFindTest extends Tests {

  public void sequential(UnionFind uf) throws Exception {
    System.out.printf("Testing %s ... ", uf.getClass());
    // Find
    assertEquals(uf.find(0), 0);
    assertEquals(uf.find(1), 1);
    assertEquals(uf.find(2), 2);
    // Union
    uf.union(1, 2);
    assertEquals(uf.find(1), uf.find(2));
    
    uf.union(2, 3);
    assertEquals(uf.find(1), uf.find(2));
    assertEquals(uf.find(1), uf.find(3));
    assertEquals(uf.find(2), uf.find(3));

    uf.union(1, 4);
    assertEquals(uf.find(1), uf.find(2));
    assertEquals(uf.find(1), uf.find(3));
    assertEquals(uf.find(2), uf.find(3));
    assertEquals(uf.find(1), uf.find(4));
    assertEquals(uf.find(2), uf.find(4));
    assertEquals(uf.find(3), uf.find(4));
  }

  public void concurrent(final int size, final UnionFind uf) throws Exception {
    final int[] numbers = new int[size];
    for (int i = 0; i < numbers.length; ++i) 
      numbers[i] = i;
    // Populate threads
    final int threadCount = 32;
    final CyclicBarrier startBarrier = new CyclicBarrier(threadCount+1), 
      stopBarrier = startBarrier;
    Collections.shuffle(Arrays.asList(numbers));
    for (int i = 0; i < threadCount; ++i) {
      Thread ti = new Thread(new Runnable() { public void run() {
        try { startBarrier.await(); } catch (Exception exn) { }
        for (int j=0; j<100; j++)
          for (int i = 0; i < numbers.length - 1; ++i) 
            uf.union(numbers[i], numbers[i + 1]);
        try { stopBarrier.await(); } catch (Exception exn) { }
      }});
      ti.start();
    }
    startBarrier.await();
    stopBarrier.await();
    final int root = uf.find(0);
    for (int i : numbers) {
      assertEquals(uf.find(i), root);
    }
    System.out.println("passed");
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

// Coarse-locking union-find.  All operations lock on the entire data
// structure.

class CoarseUnionFind implements UnionFind {
  private final Node[] nodes;

  public CoarseUnionFind(int count) {
    this.nodes = new Node[count];
    for (int x=0; x<count; x++)
      nodes[x] = new Node(x);
  }

  public synchronized int find(int x) {
    while (nodes[x].next != x) {
      final int t = nodes[x].next, u = nodes[t].next;
      nodes[x].next = u;
      x = u;
    }
    return x;
  }

  public synchronized void union(int x, int y) {
    int rx = find(x), ry = find(y);
    if (rx == ry)
      return;
    if (nodes[rx].rank > nodes[ry].rank) {
      int tmp = rx; rx = ry; ry = tmp;
    }
    // Now nodes[rx].rank <= nodes[ry].rank
    nodes[rx].next = ry;
    if (nodes[rx].rank == nodes[ry].rank)
      nodes[ry].rank++;
  }

  public synchronized boolean sameSet(int x, int y) {
    return find(x) == find(y);
  }

  class Node {
    private int next, rank;

    public Node(int next) {
      this.next = next;
    }
  }
}

// Fine-locking union-find.  Union and sameset lock on the intrinsic
// locks of the two root Nodes involved.  Find is wait-free, takes no
// locks, and performs no compression.  

// The nodes[] array entries are never updated after initialization
// inside the constructor, so no need to worry about their visibility.
// But the fields of Node objects are written (by union and compress
// while holding locks), and read by find without holding locks, so
// must be made volatile.

class FineUnionFind implements UnionFind {
  private final Node[] nodes;

  public FineUnionFind(int count) {
    this.nodes = new Node[count];
    for (int x=0; x<count; x++)
      nodes[x] = new Node(x);
  }

  public int find(int x) {
    while (nodes[x].next != x) 
      x = nodes[x].next;
    return x;
  }

  public void union(final int x, final int y) {
    while (true) {
      int rx = find(x), ry = find(y);
      if (rx == ry)
        return;
      else if (rx > ry) { 
        int tmp = rx; rx = ry; ry = tmp; 
      }
      // Now rx < ry; take locks in consistent order
      synchronized (nodes[rx]) { 
        synchronized (nodes[ry]) {
          // Check rx, ry are still roots, else restart
          if (nodes[rx].next != rx || nodes[ry].next != ry)
            continue;
          if (nodes[rx].rank > nodes[ry].rank) {
            int tmp = rx; rx = ry; ry = tmp;
          }
          // Now nodes[rx].rank <= nodes[ry].rank
          nodes[rx].next = ry;
          if (nodes[rx].rank == nodes[ry].rank)
            nodes[ry].rank++;
          compress(x, ry);
          compress(y, ry);
        } }  
    } 
  }

  // Assumes lock is held on nodes[root]
  private void compress(int x, final int root) {
    while (nodes[x].next != x) {
      int next = nodes[x].next;
      nodes[x].next = root;
      x = next;
    }
  }

  public boolean sameSet(int x, int y) {
    return find(x) == find(y);
  }

  class Node {
    private volatile int next, rank;

    public Node(int next) {
      this.next = next;
    }
  }
}


// Wait-free CAS-based union-find, a la Anderson and Woll.

class WaitFreeUnionFind implements UnionFind {
  private final AtomicReferenceArray<Node> nodes;

  public WaitFreeUnionFind(int count) {
    this.nodes = new AtomicReferenceArray<Node>(count);
    for (int x=0; x<count; x++)
      nodes.set(x, new Node(x, 0));
  }

  private boolean updateRoot(int x, int oldRank, int y, int newRank) {
    final Node oldNode = nodes.get(x);
    if (oldNode.next.get() != x || oldNode.rank != oldRank)
      return false;
    Node newNode = new Node(y, newRank);
    return nodes.compareAndSet(x, oldNode, newNode);
  }

  public int find(int x) {
    while (nodes.get(x).next.get() != x) {
      final int t = nodes.get(x).next.get(), 
        u = nodes.get(t).next.get();
      nodes.get(x).next.compareAndSet(t, u);
      x = u;
    }
    return x;
  }

  public void union(int x, int y) {
    int xr, yr;
    do {
      x = find(x); 
      y = find(y);
      if (x == y)
        return;
      xr = nodes.get(x).rank;
      yr = nodes.get(y).rank;
      if (xr > yr || xr == yr && x > y) {
        { int tmp = x; x = y; y = tmp; }
        { int tmp = xr; xr = yr; yr = tmp; }
      }
    } while (!updateRoot(x, xr, y, xr));
    if (xr == yr) 
      updateRoot(y, yr, y, yr+1);
    setRoot(x);    
  }

  private void setRoot(int x) {
    int y = x;
    while (y != nodes.get(y).next.get()) {
      final int t = nodes.get(y).next.get(),
        u = nodes.get(t).next.get();
      nodes.get(y).next.compareAndSet(t, u);
      y = u;
    }
    updateRoot(y, nodes.get(x).rank, y, nodes.get(x).rank + 1);
  }


  public boolean sameSet(int x, int y) {
    do {
      x = find(x);
      y = find(y);
      if (x == y) 
        return true;
    } while (nodes.get(x).next.get() != x);
    return false;
  }

  class Node {
    private final AtomicInteger next;
    private final int rank;

    public Node(int next, int rank) {
      this.next = new AtomicInteger(next);
      this.rank = rank;
    }
  }
}
