package ru.ifmo.ctddev.golchin.concurrent;

import com.sun.istack.internal.Nullable;
import com.sun.org.apache.bcel.internal.generic.ALOAD;
import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.concurrent.ScalarIP;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Roman on 18/03/2017.
 */
public class IterativeParallelism implements ListIP {

    private static <T, S> S foldInit(BiFunction<S, T, S> fun, S init, ListIterator<? extends T> iterator, int len) {
        S res = init;
        while (iterator.hasNext() && len > 0) {
            res = fun.apply(res, iterator.next());
            len--;
        }
        return res;
    }


    private static <T> List<T> makeInitList(int n, @Nullable Class<T> token, @Nullable T init) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < n + 1; i++) {
            try {
                if (init == null) {
                    list.add(token.newInstance());
                } else
                    list.add(init);
            } catch (IllegalAccessException | InstantiationException e) {
            }
        }
        return list;
    }


//    private static <T> T concurrentFold(BiFunction<T, T, T> fun, List<? extends T> list, int threads) throws InterruptedException {
//        if (list.isEmpty())
//            throw new IllegalArgumentException("passed empty list");
//        return concurrentFoldInit(fun, list.get(0), fun, list, threads);
//    }

    private static <T, S> S concurrentFoldInit(BiFunction<S, T, S> fun, List<? extends S> init, BiFunction<S, S, S> resFun, List<? extends T> list, int threads) throws InterruptedException {
        Object[] arr = new Object[threads];
        List<Thread> th = new ArrayList<>();
        for (int i = 0; i < threads; ++i) {
            final int size = i != threads - 1 ? list.size() / threads : list.size() / threads + list.size() % threads;
            final int index = i;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    ListIterator<? extends T> iterator = list.listIterator(index * (list.size() / threads));
                    arr[index] = foldInit(fun, init.get(index), iterator, size);
                }
            });
            t.start();
            th.add(t);
        }
        for (Thread t : th)
            t.join();
        S res = init.get(threads);
        for (int i = 0; i < arr.length; ++i)
            res = resFun.apply(res, (S) arr[i]);
        return res;
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        BiFunction<T, T, T> fun = (x, y) -> comparator.compare(x, y) < 0 ? y : x;
        return concurrentFoldInit(fun, makeInitList(threads, null, list.get(0)), fun, list, threads);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, list, (x, y) -> -comparator.compare(x, y));
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return concurrentFoldInit((b, t) -> b && predicate.test(t), makeInitList(threads, null, true), (a, b) -> a && b, list, threads);
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, list, x -> !predicate.test(x));
    }


    @Override
    public String join(int i, List<?> list) throws InterruptedException {
        return concurrentFoldInit((sb, s) -> {sb.append(s); return sb;}, makeInitList(i, StringBuilder.class, null),
                (sb1, sb2) -> {sb1.append(sb2); return sb1; }, list, i).toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> filter(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return concurrentFoldInit((ts, t) -> {if (predicate.test(t)) ts.add(t); return ts; },
                makeInitList(i, ArrayList.class, null), (l1, l2) -> {l1.addAll(l2); return l1;}, list, i);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, U> List<U> map(int i, List<? extends T> list, Function<? super T, ? extends U> function) throws InterruptedException {
        return concurrentFoldInit((ts, t) -> {ts.add(function.apply(t)); return ts; },
                makeInitList(i, ArrayList.class, null), (l1, l2) -> {l1.addAll(l2); return l1;}, list, i);
    }

    public static void main(String[] args) throws InterruptedException {
        System.err.println(new ArrayList<>().addAll(Collections.singleton(1)));
        IterativeParallelism ip = new IterativeParallelism();
        Predicate<Integer> even = (x) -> x % 2 == 0;
        try {
            System.err.println(ip.all(2, new ArrayList<>(), even));
//            ip.maximum(2, new ArrayList<>(), Integer::compare);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        List<Integer> list = randomList(10);
        for (int threads = 1; threads <= 10; threads++) {
            System.err.println("threads number " + threads);
//            System.err.println(ip.map(2, Arrays.asList(2, 4, 3, 1), (x) -> x * 2));
            Integer myMaximum = ip.maximum(threads, list, Integer::compare);
            Integer max = list.get(0);
            for (Integer l : list)
                if (max < l)
                    max = l;
            System.err.println(max + " " + myMaximum);
            assert max == myMaximum;
            System.err.println(myMaximum);
            System.err.println(ip.any(2, list, even));
        }
//        System.err.println(ip.join(3, list));
    }


    private static List<Integer> randomList(int size) {
        final Random random = new Random(3257083275083275083L);
        final int[] pool = random.ints(Math.min(size, 1000_000)).toArray();
        List<Integer> integers =
                IntStream.generate(() -> pool[random.nextInt(pool.length)]).boxed().limit(size).collect(Collectors.toList());
        System.err.println(integers);
        return integers;
    }

}
