package ru.ifmo.ctddev.golchin.crawler;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Roman on 12/02/2017.
 */
public interface HashingAlgorithm {

    String hash(InputStream is) throws IOException;
}
