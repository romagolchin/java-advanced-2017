package ru.ifmo.ctddev.golchin.walk;


import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Roman on 12/02/2017.
 */
class FNV1Hash implements HashingAlgorithm {
    private static final int BUF_SIZE = 8192;
    private static final int FNV_PRIME = 0x01000193;
    private static final int FNV_SEED = 0x811C9DC5;

    public static final String ERROR_HASH = String.format("%08x", 0);

    @Override
    public String hash(InputStream is) throws IOException {
        int hash = FNV_SEED;
        int size;
        byte[] buffer = new byte[BUF_SIZE];
        while ((size = is.read(buffer)) >= 0) {
            hash = hash(buffer, 0, size, hash);
        }
        return String.format("%08x", hash);
    }

    public int hash(byte[] bytes, int offset, int length, int seed) {
        int hash = seed;
        for (int i = offset; i < offset + length; i++) {
            // using & to prevent sign-extending of byte
            hash = (hash * FNV_PRIME) ^ (bytes[i] & 0xff);
        }
        return hash;
    }
}
