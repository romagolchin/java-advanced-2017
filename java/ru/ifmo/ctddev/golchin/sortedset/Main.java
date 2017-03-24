package ru.ifmo.ctddev.golchin.sortedset;

import java.util.*;

/**
 * Created by Roman on 05/03/2017.
 */
public class Main {

    public static void main(String[] args) {
        final int PERFORMANCE_SIZE = 100_000;
        ArraySet<Integer> as = new ArraySet<>(
                new ArraySet<>(Arrays.asList(1, 2, 2), null)
        );
//        System.out.println(as.tailSet(null));
        Iterator<Integer> iterator = as.iterator();
        while (iterator.hasNext()) {
            System.out.println(iterator.next());
//            iterator.remove();
        }
        System.out.println(as);
        List<Integer> test = new ArrayList<>();
        for (int i = 0; i < PERFORMANCE_SIZE; i++)
            test.add(i);
        final SortedSet<Integer> set = new ArraySet<>(test);
        System.out.println(set.size() + " " + test.size());
        boolean f = true;
        for (final Integer element : set) {
            f &= set.tailSet(element).contains(element);
        }
        System.out.println(f);
    }

}
