import java.util.Arrays;
import java.util.Collections;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.function.DoubleFunction;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

class SortingPipelineStreams {
    public static void main(String[] args) {
        var size = 2;
        var stage1 = getStage(size, new PriorityQueue<Double>());
        var stage2 = getStage(size, new PriorityQueue<Double>());
        var stage3 = getStage(size, new PriorityQueue<Double>());

        var input = new Random().doubles(0, 10).limit(size * 3);
        var flushing = new Random().doubles(11, 12).limit(size * 3);
        var l = DoubleStream.concat(input, flushing).flatMap(d -> (DoubleStream) stage1.apply(d))
                .flatMap(d -> (DoubleStream) stage2.apply(d)).flatMap(d -> (DoubleStream) stage3.apply(d));

        var r = l.toArray();
        System.out.println(Arrays.toString(r));
    }

    private static DoubleFunction getStage(int maxSize, PriorityQueue<Double> heap) {
        return (n -> {
            heap.add(n);
            if (heap.size() > maxSize)
                return DoubleStream.of(heap.poll());
            return DoubleStream.empty();
        });
    }
}

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
}
