package ru.ifmo.ctddev.golchin.walk;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Created by Roman on 09/02/2017.
 */

class WalkImpl {
    private DirectoryHandlingMode mode;
    private HashingAlgorithm hashingAlgorithm;
    private String inputFileName;
    private String outputFileName;
    private String inputCharset;
    private String outputCharset;
    private static final String IO_ERROR = "an I/O error occurred while processing the file: ";


    public WalkImpl(String[] args, String inputCharset, String outputCharset,
                    HashingAlgorithm hashingAlgorithm, DirectoryHandlingMode directoryHandlingMode) throws IllegalArgumentException {
        if (args.length < 2) {
            throw new IllegalArgumentException("expected 2 file names, got " + args.length);
        }
        this.inputFileName = args[0];
        this.outputFileName = args[1];
        this.inputCharset = inputCharset;
        this.outputCharset = outputCharset;
        this.hashingAlgorithm = hashingAlgorithm;
        mode = directoryHandlingMode;
    }

    private void write(Writer writer, String message) {
        try {
            writer.write(message + "\n");
        } catch (IOException e) {
            System.err.println(IO_ERROR + " " + outputFileName);
        }
    }

    void walk() {
        try (Reader in = new InputStreamReader(new FileInputStream(inputFileName), inputCharset)) {
            try (Writer writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outputFileName), outputCharset))) {
                BufferedReader reader = new BufferedReader(in);
                String filePath;
                while ((filePath = reader.readLine()) != null) {
                    try {
                        Path path = new File(filePath).toPath();
                        handlePath(path, writer);
                    } catch (FileNotFoundException e) {
                        System.err.println("Unable to find path to calculate hash of: " + filePath);
                        writer.write(FNV1Hash.ERROR_HASH + " " + filePath + "\n");
                    }
                }
            } catch (FileNotFoundException e) {
                System.err.println("Unable to find / create output file " + outputFileName);
            } catch (IOException e) {
                /* Failed to close output file */
                System.err.println("Failed to close output file");
            } catch (InvalidPathException e) {
                System.err.println("invalid path");
            }
        } catch (FileNotFoundException e) {
            System.err.println("Unable to find input file " + inputFileName);

        } catch (IOException e) {
            /* Failed to close input file or failed to set charset */
        }
    }
    /**
     * @param filePath syntactically valid file path
     * @throws FileNotFoundException
     */
    private void hash(final Path filePath, final Writer writer) throws FileNotFoundException {
        try (FileInputStream stream = new FileInputStream(filePath.toFile())) {
            String fileHash = FNV1Hash.ERROR_HASH;
            try {
                fileHash = hashingAlgorithm.hash(stream);
            } catch (IOException e) {
                System.err.println(IO_ERROR + filePath);
            }
            write(writer, fileHash + " " + filePath);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            System.err.println("Failed to close file");
        }
    }

    private void handleDirectory(Path dir, final Writer writer) throws FileNotFoundException, SecurityException {
        switch (mode) {
            case ERROR: {
                System.err.println("Passed directory instead of file: " + dir.toString());
                write(writer, FNV1Hash.ERROR_HASH + " " + dir);
                break;
            }
            case RECURSIVE_WALK: {

                try {
                    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            hash(file, writer);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    write(writer, FNV1Hash.ERROR_HASH + " " + dir);
                    System.err.println("an I/O error occurred while walking directory: " + dir);
                }
            }
        }
    }

    private void handlePath(Path path, Writer writer) throws FileNotFoundException {
        try {
            if (Files.isDirectory(path)) {
                handleDirectory(path, writer);
            } else
                hash(path, writer);
        } catch (SecurityException e) {
            System.err.println("You don't have permission to read this file/directory: " + path);
        }
    }
}
