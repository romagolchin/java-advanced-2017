package ru.ifmo.ctddev.golchin;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
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
    private List<String> downloaded;
    private final ConcurrentSkipListSet<String> visited;
    private ExecutorService downloadExecutorService;
    private ExecutorService extractExecutorService;
    private ForkJoinPool forkJoinPool;



    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.downloaders = downloaders;
        this.extractors = extractors;
        this.perHost = perHost;
        downloadExecutorService = Executors.newFixedThreadPool(downloaders);
        extractExecutorService = Executors.newFixedThreadPool(extractors);
        errors = new ConcurrentHashMap<>();
        downloaded = new ArrayList<>();
        visited = new ConcurrentSkipListSet<>();
        forkJoinPool = new ForkJoinPool(extractors);
    }


    private class DfsTask extends RecursiveAction {
        private String url;
        private int depth;

        public DfsTask(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }

        @Override
        protected void compute() {
            if (depth > 0) {
                try {
                    Document document = downloadExecutorService.submit(
                            () -> downloader.download(url)
                    ).get();
                    downloaded.add(url);
                    TreeSet<String> uniqueNeighbours = new TreeSet<>(
                            document.extractLinks());
                    if (depth > 1) {
                        List<DfsTask> subTasks = new LinkedList<>();
                        for (String n : uniqueNeighbours) {
                            //fixme
                            synchronized (visited) {
                                if (!visited.contains(n)) {
                                    DfsTask dfsTask = new DfsTask(n, depth - 1);
                                    dfsTask.fork();
                                    visited.add(n);
                                    subTasks.add(dfsTask);
                                }
                            }
                        }
                        for (DfsTask dfsTask : subTasks)
                            dfsTask.join();
                    }
                } catch (IOException | ExecutionException e) {
                    if (e instanceof IOException)
                        errors.put(url, (IOException) e);
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
        visited.add(url);
        forkJoinPool.invoke(new DfsTask(url, depth));
        return new Result(downloaded, errors);
    }

    @Override
    public void close() {
        downloadExecutorService.shutdown();
        extractExecutorService.shutdown();
        forkJoinPool.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        int downloaders = 5;
        int extractors = 3;
        int perHost = 5;
        if (args.length > 1)
            downloaders = Integer.valueOf(args[1]);
        if (args.length > 2)
            extractors = Integer.valueOf(args[2]);
        if (args.length > 3)
            perHost = Integer.valueOf(args[3]);
        WebCrawler webCrawler = new WebCrawler(new CachingDownloader(), downloaders, extractors, perHost);
        Result result = webCrawler.download(args[0], 3);
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
                        System.out.println("page " + downloaded.get(i - 1) + " repeated " + cnt + " times" );
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
