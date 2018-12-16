
// Various implementations of k-means clustering
// sestoft@itu.dk * 2017-01-04

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestKMeans {
  public static void main(String[] args) {
    // There are n points and k clusters
    final int n = 200_000, k = 81;
    // final int n = 9, k = 3;
    final Point[] points = GenerateData.randomPoints(n);
    final int[] initialPoints = GenerateData.randomIndexes(n, k);
    for (int i = 0; i < 3; i++) {
      // timeKMeans(new KMeans1(points, k), initialPoints);
      timeKMeans(new KMeans1P(points, k), initialPoints);
      // timeKMeans(new KMeans2(points, k), initialPoints);
      // timeKMeans(new KMeans2P(points, k), initialPoints);
      // timeKMeans(new KMeans2Q(points, k), initialPoints);
      // timeKMeans(new KMeans2Stm(points, k), initialPoints);
      // timeKMeans(new KMeans3(points, k), initialPoints);
      // timeKMeans(new KMeans3P(points, k), initialPoints);
      System.out.println();
    }
  }

  public static void timeKMeans(KMeans km, int[] initialPoints) {
    Timer t = new Timer();
    km.findClusters(initialPoints);
    double time = t.check();
    // To avoid seeing the k computed clusters, comment out next line:
    km.print();
    System.out.printf("%-20s Real time: %9.3f%n", km.getClass(), time);
  }
}

interface KMeans {
  void findClusters(int[] initialPoints);

  void print();
}

// ----------------------------------------------------------------------

// QUESTION 1 KMEANS1P
class KMeans1P implements KMeans {
  private final Point[] points;
  private final int k;
  private Cluster[] clusters;
  private int iterations;

  public KMeans1P(Point[] points, int k) {
    this.points = points;
    this.k = k;
  }

  private Callable<Void> assignShit(int from, int to, Cluster[] clusters, Point[] points) {
    return () -> {
      for (int j = from; j < to; j++) {
        Point p = points[j];
        Cluster best = null;
        for (Cluster c : clusters)
          if (best == null || p.sqrDist(c.mean) < p.sqrDist(best.mean))
            best = c;
        best.add(p);
      }
      return null;
    };
  }

  private Callable<Cluster> updateShit(boolean converged, ArrayList<Cluster> newClusters, Cluster c) {
    return new Callable<Cluster>() {
      public Cluster call() {
        Point mean = c.computeMean();
        if (!c.mean.almostEquals(mean)) {
          // converged = false;
        }
        if (mean != null)
          newClusters.add(new Cluster(mean));
        else
          System.out.printf("===> Empty cluster at %s%n", c.mean);
        return null;
      }
    };
  }

  public void findClusters(int[] initialPoints) {
    Cluster[] clusters = GenerateData.initialClusters(points, initialPoints, Cluster::new, Cluster[]::new);
    AtomicBoolean converged = new AtomicBoolean();
    ExecutorService es = Executors.newWorkStealingPool();

    List<Future<Cluster>> updateFutures = new ArrayList<Future<Cluster>>();
    while (!converged.get()) {
      iterations++;
      { // Assignment step: put each point in exactly one cluster
        final int P = 8, perTask = points.length / P;
        List<Callable<Void>> assignmentTasks = new ArrayList<>();
        for (int i = 0; i < P; i++) {
          final int from = perTask * i;
          final int to = (i + 1 == P) ? points.length : perTask * (i + 1);
          final Cluster[] finaClusters = clusters;
          assignmentTasks.add(() -> {
            for (int j = from; j < to; j++) {
              final Point p = points[j];
              Cluster best = null;
              for (Cluster c : finaClusters)
                if (best == null || p.sqrDist(c.mean) < p.sqrDist(best.mean))
                  best = c;
              best.add(p);
            }
            return null;
          });
        }
        try {
          List<Future<Void>> assignmentFutures = es.invokeAll(assignmentTasks);
          for (Future<Void> fut : assignmentFutures)
            fut.get();
        } catch (Exception e) {
        }
      }
      { // Update step: recompute mean of each cluster
        converged.set(true);
        ArrayList<Cluster> newClusters = new ArrayList<>();
        final Cluster[] finalClusters1 = clusters; // effective final while working in parallel
        List<Callable<Cluster>> updateTasks = new ArrayList<>();
        for (int i = 0; i < clusters.length; i++) {
          final int clusterNumber = i;
          updateTasks.add(new Callable<Cluster>() {
            public Cluster call() {
              Cluster result = null;
              Cluster c = finalClusters1[clusterNumber];
              Point mean = c.computeMean();
              if (!c.mean.almostEquals(mean))
                converged.set(false);
              if (mean != null)
                result = new Cluster(mean);
              else
                System.out.printf("===> Empty cluster at %s%n", c.mean);
              return result;
            }
          });
        }
        try {
          updateFutures = es.invokeAll(updateTasks);
          for (Future<Cluster> fut : updateFutures) {
            Cluster c = fut.get();
            newClusters.add(c);
          }
        } catch (Exception e) {
        }
        clusters = newClusters.toArray(new Cluster[newClusters.size()]);
      }
    }
    this.clusters = clusters;
    es.shutdown();
  }

  // public void findClusters(int[] initialPoints) {
  // Cluster[] clusters = GenerateData.initialClusters(points, initialPoints,
  // Cluster::new, Cluster[]::new);
  // final AtomicBoolean converged = new AtomicBoolean();
  // ExecutorService executorService = Executors.newWorkStealingPool();
  // while (!converged.get()) {
  // iterations++;
  // { // Assignment step: put each point in exactly one cluster
  // List<Callable<Void>> tasks = new ArrayList<>();
  // final int taskCount = 8, perTask = points.length / taskCount;
  // for (int t = 0; t < taskCount; t++) {
  // final int from = perTask * t, to = (t + 1 == taskCount) ? points.length :
  // perTask * (t + 1);
  // final Cluster[] finalClusters = clusters; // effective final while we are
  // working in parallel
  // tasks.add(() -> {
  // for (int i = from; i < to; i++) {
  // final Point p = points[i];
  // Cluster best = null;
  // for (Cluster c : finalClusters)
  // if (best == null || p.sqrDist(c.mean) < p.sqrDist(best.mean))
  // best = c;
  // best.add(p);
  // }
  // return null;
  // });
  // }
  // try {
  // List<Future<Void>> futures = executorService.invokeAll(tasks);
  // for (Future<?> future : futures)
  // future.get();
  // } catch (InterruptedException exn) {
  // System.out.println("Interrupted: " + exn);
  // } catch (ExecutionException e) {
  // throw new RuntimeException(e);
  // }
  // }
  // { // Update step: recompute mean of each cluster
  // converged.set(true);
  // final ArrayList<Cluster> newClusters = new ArrayList<>();
  // final Cluster[] finalClusters1 = clusters; // effective final while working
  // in parallel
  // Future[] futures = new Future[clusters.length];
  // for (int t = 0; t < clusters.length; t++) {
  // final int thisCluster = t;
  // futures[t] = (executorService.submit((() -> {
  // Cluster c = finalClusters1[thisCluster];
  // Cluster result = null;
  // Point mean = c.computeMean();
  // if (!c.mean.almostEquals(mean))
  // converged.set(false);
  // if (mean != null)
  // result = new Cluster(mean);
  // else
  // System.out.printf("===> Empty cluster at %s%n", c.mean);
  // return result;
  // })));
  // }
  // try {
  // for (Future<Cluster> future : futures)
  // newClusters.add(future.get());
  // } catch (InterruptedException exn) {
  // System.out.println("Interrupted: " + exn);
  // } catch (ExecutionException e) {
  // throw new RuntimeException(e);
  // }

  // clusters = newClusters.toArray(new Cluster[newClusters.size()]);
  // }
  // }
  // this.clusters = clusters;
  // executorService.shutdown();
  // }

  public void print() {
    for (Cluster c : clusters)
      System.out.println(c);
    System.out.printf("Used %d iterations%n", iterations);
  }

  static class Cluster extends ClusterBase {
    private final ArrayList<Point> points = new ArrayList<>();
    private final Point mean;

    public Cluster(Point mean) {
      this.mean = mean;
    }

    @Override
    public Point getMean() {
      return mean;
    }

    public synchronized void add(Point p) {
      points.add(p);
    }

    public synchronized Point computeMean() {
      double sumx = 0.0, sumy = 0.0;
      for (Point p : points) {
        sumx += p.x;
        sumy += p.y;
      }
      int count = points.size();
      return count == 0 ? null : new Point(sumx / count, sumy / count);
    }
  }
}

class KMeans1 implements KMeans {
  // Sequential version 1. A Cluster has an immutable mean field, and
  // a mutable list of immutable Points.

  private final Point[] points;
  private final int k;
  private Cluster[] clusters;
  private int iterations;

  public KMeans1(Point[] points, int k) {
    this.points = points;
    this.k = k;
  }

  public void findClusters(int[] initialPoints) {
    Cluster[] clusters = GenerateData.initialClusters(points, initialPoints, Cluster::new, Cluster[]::new);
    boolean converged = false;
    while (!converged) {
      iterations++;
      { // Assignment step: put each point in exactly one cluster
        for (Point p : points) {
          Cluster best = null;
          for (Cluster c : clusters)
            if (best == null || p.sqrDist(c.mean) < p.sqrDist(best.mean))
              best = c;
          best.add(p);
        }
      }
      { // Update step: recompute mean of each cluster
        ArrayList<Cluster> newClusters = new ArrayList<>();
        converged = true;
        for (Cluster c : clusters) {
          Point mean = c.computeMean();
          if (!c.mean.almostEquals(mean))
            converged = false;
          if (mean != null)
            newClusters.add(new Cluster(mean));
          else
            System.out.printf("===> Empty cluster at %s%n", c.mean);
        }
        clusters = newClusters.toArray(new Cluster[newClusters.size()]);
      }
    }
    this.clusters = clusters;
  }

  public void print() {
    for (Cluster c : clusters)
      System.out.println(c);
    System.out.printf("Used %d iterations%n", iterations);
  }

  static class Cluster extends ClusterBase {
    private final ArrayList<Point> points = new ArrayList<>();
    private final Point mean;

    public Cluster(Point mean) {
      this.mean = mean;
    }

    @Override
    public Point getMean() {
      return mean;
    }

    public void add(Point p) {
      points.add(p);
    }

    public Point computeMean() {
      double sumx = 0.0, sumy = 0.0;
      for (Point p : points) {
        sumx += p.x;
        sumy += p.y;
      }
      int count = points.size();
      return count == 0 ? null : new Point(sumx / count, sumy / count);
    }
  }
}

// ----------------------------------------------------------------------

class KMeans2 implements KMeans {
  // Sequential version 2. Data represention: An array points of
  // Points and a same-index array myCluster of the Cluster to which
  // each point belongs, so that points[pi] belongs to myCluster[pi],
  // for each Point index pi. A Cluster holds a mutable mean field
  // and has methods for aggregation of its value.

  private final Point[] points;
  private final int k;
  private Cluster[] clusters;
  private int iterations;

  public KMeans2(Point[] points, int k) {
    this.points = points;
    this.k = k;
  }

  public void findClusters(int[] initialPoints) {
    final Cluster[] clusters = GenerateData.initialClusters(points, initialPoints, Cluster::new, Cluster[]::new);
    final Cluster[] myCluster = new Cluster[points.length];
    boolean converged = false;
    while (!converged) {
      iterations++;
      {
        // Assignment step: put each point in exactly one cluster
        for (int pi = 0; pi < points.length; pi++) {
          Point p = points[pi];
          Cluster best = null;
          for (Cluster c : clusters)
            if (best == null || p.sqrDist(c.mean) < p.sqrDist(best.mean))
              best = c;
          myCluster[pi] = best;
        }
      }
      {
        // Update step: recompute mean of each cluster
        for (Cluster c : clusters)
          c.resetMean();
        for (int pi = 0; pi < points.length; pi++)
          myCluster[pi].addToMean(points[pi]);
        converged = true;
        for (Cluster c : clusters)
          converged &= c.computeNewMean();
      }
      // System.out.printf("[%d]", iterations); // To diagnose infinite loops
    }
    this.clusters = clusters;
  }

  public void print() {
    for (Cluster c : clusters)
      System.out.println(c);
    System.out.printf("Used %d iterations%n", iterations);
  }

  static class Cluster extends ClusterBase {
    private Point mean;
    private double sumx, sumy;
    private int count;

    public Cluster(Point mean) {
      this.mean = mean;
    }

    public void addToMean(Point p) {
      sumx += p.x;
      sumy += p.y;
      count++;
    }

    // Recompute mean, return true if it stays almost the same, else false
    public boolean computeNewMean() {
      Point oldMean = this.mean;
      this.mean = new Point(sumx / count, sumy / count);
      return oldMean.almostEquals(this.mean);
    }

    public void resetMean() {
      sumx = sumy = 0.0;
      count = 0;
    }

    @Override
    public Point getMean() {
      return mean;
    }
  }
}

// ----------------------------------------------------------------------

class KMeans3 implements KMeans {
  // Stream-based version. Representation (A2): Immutable Clusters of
  // immutable Points.

  private final Point[] points;
  private final int k;
  private Cluster[] clusters;
  private int iterations;

  public KMeans3(Point[] points, int k) {
    this.points = points;
    this.k = k;
  }

  public void findClusters(int[] initialPoints) {
    Cluster[] clusters = GenerateData.initialClusters(points, initialPoints, Cluster::new, Cluster[]::new);
    boolean converged = false;
    while (!converged) {
      iterations++;
      { // Assignment step: put each point in exactly one cluster
        final Cluster[] clustersLocal = clusters; // For capture in lambda
        // Stream<Point> pStream = Stream.of(points);
        // Map<Cluster, List<Point>> groups =
        // pStream.collect(Collectors.groupingBy(classifier))
        // clusters = groups.entrySet().stream().map(...) ... TODO ...;
      }
      { // Update step: recompute mean of each cluster
        // Cluster[] newClusters = ... TODO ...
        // converged = Arrays.equals(clusters, newClusters);
        // clusters = newClusters;
      }
    }
    this.clusters = clusters;
  }

  public void print() {
    for (Cluster c : clusters)
      System.out.println(c);
    System.out.printf("Used %d iterations%n", iterations);
  }

  static class Cluster extends ClusterBase {
    private final List<Point> points;
    private final Point mean;

    public Cluster(Point mean) {
      this(mean, new ArrayList<>());
    }

    public Cluster(Point mean, List<Point> points) {
      this.mean = mean;
      this.points = Collections.unmodifiableList(points);
    }

    @Override
    public Point getMean() {
      return mean;
    }

    public Cluster computeMean() {
      double sumx = points.stream().mapToDouble(p -> p.x).sum(), sumy = points.stream().mapToDouble(p -> p.y).sum();
      Point newMean = new Point(sumx / points.size(), sumy / points.size());
      return new Cluster(newMean, points);
    }
  }
}

// ----------------------------------------------------------------------

// DO NOT MODIFY ANYTHING BELOW THIS LINE

// Immutable 2D points (x,y) with some basic operations

class Point {
  public final double x, y;

  public Point(double x, double y) {
    this.x = x;
    this.y = y;
  }

  // The square of the Euclidean distance between this Point and that
  public double sqrDist(Point that) {
    return sqr(this.x - that.x) + sqr(this.y - that.y);
  }

  private static double sqr(double d) {
    return d * d;
  }

  // Reasonable when original point coordinates are integers.
  private static final double epsilon = 1E-10;

  // Approximate equality of doubles and Points. There are better
  // ways to do this, but here we prefer simplicity.
  public static boolean almostEquals(double x, double y) {
    return Math.abs(x - y) <= epsilon;
  }

  public boolean almostEquals(Point that) {
    return almostEquals(this.x, that.x) && almostEquals(this.y, that.y);
  }

  @Override
  public String toString() {
    return String.format("(%17.14f, %17.14f)", x, y);
  }
}

// Printing and approximate comparison of clusters

abstract class ClusterBase {
  public abstract Point getMean();

  // Two Clusters are considered equal if their means are almost equal
  @Override
  public boolean equals(Object o) {
    return o instanceof ClusterBase && this.getMean().almostEquals(((ClusterBase) o).getMean());
  }

  @Override
  public String toString() {
    return String.format("mean = %s", getMean());
  }
}

// Generation of test data

class GenerateData {
  // Intentionally not really random, for reproducibility
  private static final Random rnd = new Random(42);

  // An array of means (centers) of future point clusters,
  // approximately arranged in a 9x9 grid
  private static final Point[] centers = IntStream.range(0, 9).boxed()
      .flatMap(x -> IntStream.range(0, 9).mapToObj(y -> new Point(x * 10 + 4, y * 10 + 4))).toArray(Point[]::new);

  // Make a random point near a randomly chosen center
  private static Point randomPoint() {
    Point orig = centers[rnd.nextInt(centers.length)];
    return new Point(orig.x + rnd.nextDouble() * 8, orig.y + rnd.nextDouble() * 8);
  }

  // Make an array of n points near some of the centers
  public static Point[] randomPoints(int n) {
    return Stream.<Point>generate(GenerateData::randomPoint).limit(n).toArray(Point[]::new);
  }

  // Make an array of k distinct random numbers of the range [0...n-1]
  public static int[] randomIndexes(int n, int k) {
    final HashSet<Integer> initial = new HashSet<>();
    while (initial.size() < k)
      initial.add(rnd.nextInt(n));
    return initial.stream().mapToInt(i -> i).toArray();
  }

  // Select k distinct Points as cluster centers, passing in functions
  // to create appropriate Cluster objects and Cluster arrays.
  public static <C extends ClusterBase> C[] initialClusters(Point[] points, int[] pointIndexes,
      Function<Point, C> makeC, IntFunction<C[]> makeCArray) {
    C[] initial = makeCArray.apply(pointIndexes.length);
    for (int i = 0; i < pointIndexes.length; i++)
      initial[i] = makeC.apply(points[pointIndexes[i]]);
    return initial;
  }
}

// Crude wall clock timing utility, measuring time in seconds

class Timer {
  private long start = 0, spent = 0;

  public Timer() {
    play();
  }

  public double check() {
    return (start == 0 ? spent : System.nanoTime() - start + spent) / 1e9;
  }

  public void pause() {
    if (start != 0) {
      spent += System.nanoTime() - start;
      start = 0;
    }
  }

  public void play() {
    if (start == 0)
      start = System.nanoTime();
  }
}
