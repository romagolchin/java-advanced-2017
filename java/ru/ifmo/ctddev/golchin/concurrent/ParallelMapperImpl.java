package ru.ifmo.ctddev.golchin.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Roman on 25/03/2017.
 */
public class ParallelMapperImpl implements ParallelMapper {

    private final Map<Integer, List> results;
    private final Queue<Task> tasks;
    private final List<Thread> pool;
    private volatile Queue<Integer> freeThreads;
    private volatile TaskPart[] taskParts;

    private final Thread scheduler;
    private int cntTasks;


    private synchronized int getCntTasks() {
        int ret = cntTasks;
        cntTasks++;
        return ret;
    }

    private <R> List<R> getResult(int id) throws InterruptedException {
        synchronized (results) {
            while (!results.containsKey(id)) {
                results.wait();
            }
            List list = results.get(id);
            results.remove(id);
            return list;
        }
    }


    public ParallelMapperImpl(final int threads) {
        pool = new ArrayList<>();
        tasks = new ArrayDeque<>();
        freeThreads = new ArrayDeque<>();
        taskParts = new TaskPart[threads];
        results = new HashMap<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(new Worker(i));
            pool.add(t);
            freeThreads.add(i);
            t.start();
        }
        scheduler = new Thread(new Scheduler());
        scheduler.start();
    }

    private synchronized void setFree(int workerId) throws InterruptedException {
        freeThreads.add(workerId);
        notifyAll();
    }

    private synchronized Integer getFree() throws InterruptedException {
        while (freeThreads.isEmpty())
            wait();
        return freeThreads.poll();
    }

    private void setTask(Task task) throws InterruptedException {
        synchronized (tasks) {
            tasks.add(task);
            tasks.notify();
        }
    }

    private Task getTask() throws InterruptedException {
        synchronized (tasks) {
            while (tasks.isEmpty())
                tasks.wait();
            return tasks.poll();
        }
    }

    private synchronized void setTaskPart(int workerId, TaskPart taskPart) throws InterruptedException {
        while (taskParts[workerId] != null) {
            wait();
        }
        taskParts[workerId] = taskPart;
        notifyAll();
    }

    private synchronized TaskPart getTaskPart(int workerId) throws InterruptedException {
        while (taskParts[workerId] == null) {
            wait();
        }
        TaskPart part = taskParts[workerId];
        taskParts[workerId] = null;
        notifyAll();
        return part;
    }


    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args) throws InterruptedException {
        int x = getCntTasks();
        setTask(new Task<T, R>(x, f, args));
        return getResult(x);
    }

    @Override
    public void close() throws InterruptedException {
        scheduler.interrupt();
        for (Thread t : pool)
            t.interrupt();
        for (Thread t : pool)
            t.join();
    }

    private class Task<T, R> {
        private final int id;
        private int restSize;
        private final Function<? super T, ? extends R> f;
        private final List<? extends T> args;
        private List<R> res;


        public Task(int id, Function<? super T, ? extends R> f, List<? extends T> args) {
            this.id = id;
            this.restSize = args.size();
            this.f = f;
            this.args = args;
            this.res = new ArrayList<>();
            for (int i = 0; i < restSize; i++)
                res.add(null);
        }

        public Function<? super T, ? extends R> getF() {
            return f;
        }

        public T getArg(int i) {
            return args.get(i);
        }

        public void setResult(int i, R result) {
            synchronized (results) {
                if (restSize > 0) {
                    res.set(i, result);
                    --restSize;
                }
                assert res != null;
                if (restSize == 0) {
                    results.put(id, res);
                    results.notifyAll();
                }
            }
        }

        public int size() {
            return args.size();
        }
    }


    private class Worker implements Runnable {
        private final int id;

        public Worker(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    TaskPart taskPart = getTaskPart(id);
                    Task task = taskPart.getTask();
                    task.setResult(taskPart.getIndex(),
                            taskPart.apply());
                    setFree(id);
                }
            } catch (InterruptedException e) {
                System.err.println("Worker " + id + " has been interrupted");
            } finally {
                Thread.currentThread().interrupt();
            }

        }
    }


    private class TaskPart<T, R> {
        private Task<T, R> task;
        private int index;

        public Task<T, R> getTask() {
            return task;
        }

        public int getIndex() {
            return index;
        }

        public TaskPart(Task<T, R> task, int index) {
            this.task = task;
            this.index = index;
        }

        public R apply() {
            return task.getF().apply(task.getArg(index));
        }
    }

    private class Scheduler implements Runnable {
        @Override
        public void run() {
            int cnt = 0;
            Task task = null;
            try {
                while (!Thread.interrupted()) {
                    if (task == null || task.size() <= cnt) {
                        task = getTask();
                        cnt = 0;
                    }
                    setTaskPart(getFree(), new TaskPart(task, cnt));
                    cnt++;
                }
            } catch (InterruptedException e) {
                System.err.println("Scheduler has been interrupted");
            } finally {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final Random random = new Random(3257083275083275083L);

    public static List<Integer> randomList(final int size) {
        final int[] ints = random.ints(Math.min(size, 1000_000)).toArray();
        return IntStream.generate(() -> ints[random.nextInt(10)]).limit(size).boxed().collect(Collectors.toList());
    }

    public static void main(String[] args) throws InterruptedException {
        ParallelMapperImpl mapper = new ParallelMapperImpl(5);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> {
                IterativeParallelism parallelism = new IterativeParallelism(mapper);
                List<Integer> list = randomList(1000);
                try {
                    System.out.println(parallelism.maximum(3, list, (x, y) -> 0));
                } catch (InterruptedException e) {
                    System.err.println("client interrupted");
                }
                List<Integer> res = new ArrayList<>();
                list.forEach(x -> res.add(2 * x));
                System.out.println(res);
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads)
            t.join();
        mapper.close();
    }
}
