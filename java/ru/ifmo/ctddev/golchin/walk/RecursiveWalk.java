package ru.ifmo.ctddev.golchin.walk;

/**
 * Created by Roman on 12/02/2017.
 */
public class RecursiveWalk {
    public static void main(String[] args) {
        try {
            WalkImpl recursiveWalkImpl = new WalkImpl(args, "UTF-8", "UTF-8", new FNV1Hash(), DirectoryHandlingMode.RECURSIVE_WALK);
            recursiveWalkImpl.walk();
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }
}
