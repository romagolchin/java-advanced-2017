package ru.ifmo.ctddev.golchin.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Created by Roman on 18/03/2017.
 */
public class IterativeParallelism implements ListIP {

    private ParallelMapper mapper;

    public IterativeParallelism() {
        this.mapper = null;
    }

    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    private static <T, S> S fold(BiFunction<S, T, S> fun, S init, List<? extends T> list) {
        S res = init;
        for (T t : list)
            res = fun.apply(res, t);
        return res;
    }


    private <T, S> S concurrentFold(BiFunction<S, T, S> fun, Supplier<S> sup, BiFunction<S, S, S> resFun, List<? extends T> list, int threads) throws InterruptedException {
        List<S> resList = new ArrayList<>();
        List<List<? extends T>> toMap = new ArrayList<>();
        for (int i = 0; i < threads; ++i) {
            int size = list.size() / threads;
            if (i == threads - 1)
                size += list.size() % threads;
            int fromIndex = i * (list.size() / threads);
            toMap.add(list.subList(fromIndex, fromIndex + size));
        }
        resList = mapper.map(l -> fold(fun, sup.get(), l), toMap);
        S res = sup.get();
        for (S a : resList)
            res = resFun.apply(res, a);
        return res;
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        BiFunction<T, T, T> fun = (x, y) -> comparator.compare(x, y) < 0 ? y : x;
        return concurrentFold(fun, () -> list.get(0), fun, list, threads);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, list, comparator.reversed());
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return concurrentFold((b, t) -> b && predicate.test(t), () -> true, (a, b) -> a && b, list, threads);
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, list, predicate.negate());
    }


    @Override
    public String join(int i, List<?> list) throws InterruptedException {
        return concurrentFold((sb, s) -> {
                    sb.append(s);
                    return sb;
                }, StringBuilder::new,
                (sb1, sb2) -> {
                    sb1.append(sb2);
                    return sb1;
                }, list, i).toString();
    }

    @Override
    public <T> List<T> filter(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return concurrentFold((ts, t) -> {
            if (predicate.test(t)) ts.add(t);
            return ts;
        }, ArrayList::new, (l1, l2) -> {
            l1.addAll(l2);
            return l1;
        }, list, i);
    }

    @Override
    public <T, U> List<U> map(int i, List<? extends T> list, Function<? super T, ? extends U> function) throws InterruptedException {
        return concurrentFold((ts, t) -> {
            ts.add(function.apply(t));
            return ts;
        }, ArrayList::new, (l1, l2) -> {
            l1.addAll(l2);
            return l1;
        }, list, i);
    }


}
