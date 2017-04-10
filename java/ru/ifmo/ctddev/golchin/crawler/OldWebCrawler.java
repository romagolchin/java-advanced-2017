package ru.ifmo.ctddev.golchin.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by Roman on 01/04/2017.
 */
public class WebCrawler implements Crawler {
    private Downloader downloader;
    private int downloaders;
    private int extractors;
    private int perHost;
    private final Map<String, IOException> errors;
    private Queue<String> downloaded;
    private final Set<String> visited;
    private ExecutorService downloadExecutorService;
    private ExecutorService extractExecutorService;
    private ForkJoinPool forkJoinPool;

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.downloaders = downloaders;
        this.extractors = extractors;
        this.perHost = perHost;
        System.err.println(downloaders + " " + extractors + " " + perHost);
        downloadExecutorService = Executors.newFixedThreadPool(Math.min(downloaders, 50));
        extractExecutorService = Executors.newFixedThreadPool(Math.min(extractors, 50));
        errors = new HashMap<>();
        downloaded = new ArrayBlockingQueue<>(2000);
        visited = new HashSet<>();
        forkJoinPool = new ForkJoinPool(20);
    }


    private class DfsTask extends RecursiveAction {
        private String url;
        private int depth;

        public DfsTask(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }

        private Document computeLittle(String url) throws InterruptedException {
            try {
                Document document = downloadExecutorService.submit(
                        () -> downloader.download(url)
                ).get();
                downloaded.add(url);
                return document;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException) {
                    errors.put(url, (IOException) e.getCause());
                }
            }
            return null;
        }

        private void computeLess(String url) {
            try (InputStream inputStream = new URL(url).openStream()) {
                downloaded.add(url);
            } catch(IOException e) {
                errors.put(url, e);
            }
        }


        @Override
        protected void compute() {
//            System.err.println(Thread.currentThread().getName());
            if (depth > 0) {
                try {
//                    Document document = downloadExecutorService.submit(
//                            () -> downloader.download(url)
//                    ).get();
//                    downloaded.add(url);
//                    TreeSet<String> uniqueNeighbours = new TreeSet<>(neighbours);
                    Document document = computeLittle(url);
                    if (depth > 1 && document != null) {

                        List<String> neighbours = extractExecutorService.submit(() -> document.extractLinks()).get();
                        List<DfsTask> subTasks = new LinkedList<>();
                        for (String neighbour : neighbours) {
                            //fixme
//                            if (!visited.contains(neighbour))
                            synchronized (visited) {
                                if (!visited.contains(neighbour)) {
                                    visited.add(neighbour);
                                    if (depth > 2) {
                                        DfsTask dfsTask = new DfsTask(neighbour, depth - 1);
                                        dfsTask.fork();
                                        subTasks.add(dfsTask);
                                    } else
//                                        downloaded.add(neighbour);
                                        computeLittle(neighbour);
                                }
                            }
                        }
                        for (DfsTask dfsTask : subTasks)
                            dfsTask.join();
                    }
                } catch (ExecutionException e) {
//                    if (e instanceof IOException)
//                        errors.put(url, (IOException) e);
                    if (e.getCause() instanceof IOException)
                        errors.putIfAbsent(url, (IOException) e.getCause());
                } catch (InterruptedException e) {
                    System.err.println("interrupted");
                }
            }
        }
    }


    @Override
    public Result download(String url, int depth) {
        long start = System.currentTimeMillis();
        visited.add(url);
        forkJoinPool.invoke(new DfsTask(url, depth));
        System.out.println("===== completed in " + (System.currentTimeMillis() - start));
        return new Result(new ArrayList<>(downloaded), errors);
    }

    @Override
    public void close() {
        downloadExecutorService.shutdown();
        extractExecutorService.shutdown();
        forkJoinPool.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        final String TEST_URL = "http://neerc.ifmo.ru/subregions/index.html";
        int downloaders = 5;
        int extractors = 3;
        int perHost = 5;
        if (args.length > 1)
            downloaders = Integer.valueOf(args[1]);
        if (args.length > 2)
            extractors = Integer.valueOf(args[2]);
        if (args.length > 3)
            perHost = Integer.valueOf(args[3]);
        WebCrawler webCrawler = new WebCrawler(new ReplayDownloader(TEST_URL, 3, 100, 100), downloaders, extractors, perHost);
        Result result = webCrawler.download(TEST_URL, 3);
        System.out.println("downloaded");
        List<String> downloaded = new ArrayList<>(result.getDownloaded());
        int unique = new TreeSet<>(downloaded).size();
        webCrawler.close();
        downloaded.sort(null);
        if (downloaded.size() > unique) {
            System.err.println(downloaded.size() + " " + unique);
            for (int i = 1, cnt = 1; i < downloaded.size() + 1; i++)
                if (i < downloaded.size() && downloaded.get(i - 1).equals(downloaded.get(i)))
                    cnt++;
                else {
                    if (cnt > 1)
                        System.out.println("page " + downloaded.get(i - 1) + " repeated " + cnt + " times");
                    cnt = 1;
                }
            throw new RuntimeException("not unique downloads");
        }
        for (String d : downloaded)
            System.out.print(d + " ");
        System.out.println("\nerrors");
        for (String e : result.getErrors().keySet())
            System.out.print(e + " ");
        System.out.println();
    }

}
