import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.Executors.newWorkStealingPool;

/**
 * Created by ander on 10-01-2017.
 */
public class KMeans1P implements KMeans {
    // Sequential version 1. A Cluster has an immutable mean field, and
    // a mutable list of immutable Points.

    private final Point[] points;
    private final int k;
    private Cluster[] clusters;
    private int iterations;

    public KMeans1P(Point[] points, int k) {
        this.points = points;
        this.k = k;
    }

    public void findClusters(int[] initialPoints) {
        Cluster[] clusters = GenerateData.initialClusters(points, initialPoints, Cluster::new, Cluster[]::new);
        final AtomicBoolean converged = new AtomicBoolean();
        ExecutorService executorService = newWorkStealingPool();
        while (!converged.get()) {
            iterations++;
            { // Assignment step: put each point in exactly one cluster
                List<Callable<Void>> tasks = new ArrayList<>();
                final int taskCount = 8, perTask = points.length / taskCount;
                for (int t = 0; t < taskCount; t++) {
                    final int from = perTask * t, to = (t + 1 == taskCount) ? points.length : perTask * (t + 1);
                    final Cluster[] finalClusters = clusters; // effective final while we are working in parallel
                    tasks.add(() -> {
                        for (int i = from; i < to; i++) {
                            final Point p = points[i];
                            Cluster best = null;
                            for (Cluster c : finalClusters)
                                if (best == null || p.sqrDist(c.mean) < p.sqrDist(best.mean))
                                    best = c;
                            best.add(p);
                        }
                        return null;
                    });
                }
                try {
                    List<Future<Void>> futures = executorService.invokeAll(tasks);
                    for (Future<?> future : futures)
                        future.get();
                } catch (InterruptedException exn) {
                    System.out.println("Interrupted: " + exn);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            { // Update step: recompute mean of each cluster
                converged.set(true);
                final ArrayList<Cluster> newClusters = new ArrayList<>();
                final Cluster[] finalClusters1 = clusters; // effective final while working in parallel
                Future[] futures = new Future[clusters.length];
                for (int t = 0; t < clusters.length; t++) {
                    final int thisCluster = t;
                    futures[t] = (executorService.submit((() -> {
                        Cluster c = finalClusters1[thisCluster];
                        Cluster result = null;
                        Point mean = c.computeMean();
                        if (!c.mean.almostEquals(mean))
                            converged.set(false);
                        if (mean != null)
                            result = new Cluster(mean);
                        else
                            System.out.printf("===> Empty cluster at %s%n", c.mean);
                        return result;
                    })));
                }
                try {
                    for (Future<Cluster> future : futures)
                        newClusters.add(future.get());
                } catch (InterruptedException exn) {
                    System.out.println("Interrupted: " + exn);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }

                clusters = newClusters.toArray(new Cluster[newClusters.size()]);
            }
        }
        this.clusters = clusters;
        executorService.shutdown();
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

        private final Object lock = new Object();

        public void add(Point p) {
            synchronized (lock) {
                points.add(p);
            }
        }

        public Point computeMean() {
            double sumx = 0.0, sumy = 0.0;
            int count = 0;
            synchronized (lock) {
                for (Point p : points) {
                    sumx += p.x;
                    sumy += p.y;
                }
                count = points.size();
            }
            return count == 0 ? null : new Point(sumx / count, sumy / count);
        }
    }
}