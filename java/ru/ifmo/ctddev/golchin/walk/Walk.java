package ru.ifmo.ctddev.golchin.crawler;



/**
 * Created by Roman on 12/02/2017.
 */
public class Walk {
    public static void main(String[] args) {
        try {
            WalkImpl walkImpl = new WalkImpl(args, "UTF-8", "UTF-8", new FNV1Hash(), DirectoryHandlingMode.ERROR);
            walkImpl.walk();
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }
}
