package ru.ifmo.ctddev.golchin.sortedset;

import java.util.*;

/**
 * Created by Roman on 05/03/2017.
 */
public class ArraySet<E> extends AbstractSet<E> implements SortedSet<E> {
    private List<E> elements;
    private Comparator<? super E> comparator;

    public ArraySet() {
        elements = Collections.emptyList();
    }

    public ArraySet(Comparator<? super E> comparator) {
        this.comparator = comparator;
    }


    public ArraySet(Collection<? extends E> collection) {
        this(collection, null);
    }

    public ArraySet(Collection<? extends E> collection, Comparator<? super E> comparator) {
        TreeSet<E> treeSet = new TreeSet<>(comparator);
        treeSet.addAll(collection);
        elements = new ArrayList<>(treeSet);
        this.comparator = comparator;
    }

    private ArraySet(List<E> list, Comparator<? super E> comparator) {
        this.elements = list;
        this.comparator = comparator;
    }


    public ArraySet(SortedSet<E> set) {
        this.comparator = set.comparator();
        elements = new ArrayList<>(set);
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public ArraySet<E> subSet(Object fromElement, Object toElement) {
        int fromPos = binarySearch(fromElement);
        int toPos = binarySearch(toElement);
        return getRange(fromPos, toPos);
    }

    @Override
    public ArraySet<E> headSet(Object toElement) {
        return getRange(0, binarySearch(toElement));
    }

    @Override
    public ArraySet<E> tailSet(Object fromElement) {
        return getRange(binarySearch(fromElement), size());
    }

    @Override
    public E first() {
        if (isEmpty())
            throw new NoSuchElementException();
        return elements.get(0);
    }

    @Override
    public E last() {
        if (isEmpty())
            throw new NoSuchElementException();
        return elements.get(size() - 1);
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        try {
           assert (elements instanceof RandomAccess);
            return Collections.binarySearch(elements, (E) o, comparator) >= 0;
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < size();
            }

            @Override
            public E next() {
                index++;
                return elements.get(index - 1);
            }
        };
    }


    @SuppressWarnings("unchecked")
    private int binarySearch(Object o) {
        try {
            int pos = Collections.binarySearch(elements, (E) o, comparator);
            return pos >= 0 ? pos : -pos - 1;
        } catch (ClassCastException e) {
            System.err.println("attempt to find element of other type " + o.getClass().getName());
            return -1;
        }
    }

    private ArraySet<E> getRange(int fromPos, int toPos) {
        List<E> range = fromPos <= toPos ? elements.subList(fromPos, toPos) : Collections.emptyList();
        return new ArraySet<>(range, comparator);
    }


}
