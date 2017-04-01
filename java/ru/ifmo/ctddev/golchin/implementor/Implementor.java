package ru.ifmo.ctddev.golchin.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Class that is able to create implementation of a given interface.
 * Output is either source code or JAR file
 * @author Roman Golchin (romagolchin@gmail.com)
 */
public class Implementor implements JarImpler {

    /**
     * Convenience method that writes a string followed by line break to a writer
     *
     * @param writer {@link java.io.Writer} to be written to
     * @param line   {@link java.lang.String} to be written
     */
    private void writeln(Writer writer, String line) {
        try {
            writer.write(line + "\n");
        } catch (IOException e) {
            System.err.println("failed to write to source file");
        }
    }


    /**
     * Call to the method is equivalent to call to
     * <code>{@link #writeln(Writer, String)}</code> with the writer and empty string as arguments
     *
     * @param writer {@link java.io.Writer} to be written to
     */
    private void writeln(Writer writer) {
        writeln(writer, "");
    }


    /**
     * Returns source directory corresponding to the type token's package name. The path is formed according to rules
     * stated in documentation for {@link #implement(Class, Path)}
     * @param token the type token
     * @param path path
     * @return path to source directory
     */
    private static Path getSourceDirectory(Class<?> token, Path path) {
        String packageName = getPackageName(token);
        System.err.println("packageName " + packageName);
        return path.resolve(packageName.replace('.', File.separatorChar));
    }

    /**
     * Returns package name of the given type token
     * @param token the type token
     * @return token's package name
     */
    private static String getPackageName(Class<?> token) {
        String pre = token.getPackage().toString().split(" ")[1];
        Matcher m = Pattern.compile("[^a-zA-Z.]").matcher(pre);
        return m.replaceAll("");
    }


    /**
     * {@inheritDoc}
     *
     *
     * @param token type token to create implementation for.
     * @param path root directory.
     * @throws info.kgeorgiy.java.advanced.implementor.ImplerException if the type token does not represent an interface
     */
    @Override
    public void implement(Class<?> token, Path path) throws ImplerException {
        System.err.println("path " + path);
        String name = token.getSimpleName();
        if (!token.isInterface()) {
            throw new ImplerException();
        }
        String directoryName = getSourceDirectory(token, path).toString();
        File directory = new File(directoryName);
        try {
            Files.createDirectories(directory.toPath());
            String sourceFileName = directoryName + File.separator + name + "Impl.java";
            System.err.println("directoryName " + directoryName);
            System.err.println("sourceFileName " + sourceFileName);

            try (Writer writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(sourceFileName), StandardCharsets.UTF_8))) {
                String implName = name + "Impl";
                writeln(writer, "package " + getPackageName(token) + ";");
                writeln(writer);
                writeln(writer, "class " + implName + " implements " + name + " {");
                for (Method method : token.getMethods()) {
                    if (!Modifier.isStatic(method.getModifiers())) {
                        writeln(writer);
                        StringBuilder declarationBuilder = new StringBuilder();
                        Class<?> type = method.getReturnType();
                        declarationBuilder.append("public ")
                                .append(type.getCanonicalName())
                                .append(" ")
                                .append(method.getName())
                                .append(" ");
                        StringJoiner exceptionJoiner = new StringJoiner(", ", " throws ", "");
                        exceptionJoiner.setEmptyValue(" ");
                        for (Class<?> exceptionType : method.getExceptionTypes())
                            exceptionJoiner.add(exceptionType.getCanonicalName());
                        StringJoiner parameterJoiner = new StringJoiner(", ", "(", ")");
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        int i = 0;
                        for (Class<?> param : parameterTypes) {
                            parameterJoiner.add(param.getCanonicalName() + " param" + i);
                            ++i;
                        }
                        declarationBuilder.append(parameterJoiner).append(exceptionJoiner).append(" {\n");
                        String returnString;
                        if (type.isPrimitive()) {
                            if (type == Boolean.TYPE)
                                returnString = "return false;";
                            else if (type == Void.TYPE)
                                returnString = "";
                            else
                                returnString = "return 0;";
                        } else
                            returnString = "return null;";
                        declarationBuilder.append(returnString).append("\n}");
                        writeln(writer, declarationBuilder.toString());
                    }
                }
                writeln(writer, "}");

            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            System.err.println("an I/O error occurred while creating directory " + directoryName);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param token type token to create implementation for.
     * @param jarPath target <tt>.jar</tt> file.
     * @throws info.kgeorgiy.java.advanced.implementor.ImplerException if the type token does not represent an interface
     */
    public void implementJar(Class<?> token, Path jarPath) throws ImplerException {
        Path path = jarPath.subpath(0, 1);
        System.err.println("implementJar path " + path);
        implement(token, path);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Path sourceDirectory = getSourceDirectory(token, path);
        File toCompile = sourceDirectory.resolve(token.getSimpleName() + "Impl.java").toFile();
        try {
            Path buildPath = path.resolve("build");
            Files.createDirectories(buildPath);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, null, null, new ArrayList<>(Arrays.asList("-d", buildPath.toString())), null,
                    fileManager.getJavaFileObjectsFromFiles(
                            Collections.singleton(toCompile)
                    ));
            task.call();
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            Files.createDirectories(jarPath.subpath(0, jarPath.getNameCount() - 1));
            JarOutputStream jarOutputStream = new JarOutputStream(
                    new FileOutputStream(jarPath.toString()), manifest
            );
            Path entryPath = path.relativize(sourceDirectory).resolve(
                    token.getSimpleName() + "Impl.class");
            JarEntry jarEntry = new JarEntry(entryPath.toString().replace(File.separatorChar, '/'));
            System.err.println("jarEntry name " + jarEntry.getName());
            jarOutputStream.putNextEntry(jarEntry);
            String classFilePath = buildPath.resolve(entryPath).toString();
            try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(classFilePath))) {
                int size;
                byte[] buf = new byte[8192];
                while ((size = stream.read(buf)) >= 0)
                    jarOutputStream.write(buf, 0, size);

            } catch (FileNotFoundException e) {
                System.err.println("failed to find class file " + classFilePath);
            } catch (IOException e) {
                System.err.println("an I/O error occurred while writing entry " + jarEntry.getName());
            }
            jarOutputStream.close();
        } catch (IOException e) {
            System.err.println("failed to create build directory");
        }
    }


    /**
     * Entry point of the program.
     * Usage:
     * <ul>
     *     <li><tt>Implementor &lt;full.class.name&gt; &lt;path/to/build/in&gt; </tt></li>
     *     <li><tt>Implementor -jar &lt;full.class.name&gt; &lt;path/to/jarFile.jar&gt; </tt></li>
     * </ul>
     * @param args command line arguments
     * @throws ImplerException if the type token does not represent an interface
     */
    public static void main(String[] args) throws ImplerException {
        String className = null;
        try {
            Implementor implementor = new Implementor();
            if (args.length > 2 && args[0].equals("-jar")) {
                className = args[1];
                implementor.implementJar(Class.forName(args[1]), Paths.get(args[2]));
            } else if (args.length > 1) {
                className = args[0];
                implementor.implement(Class.forName(args[0]), Paths.get(args[1]));
            }
        } catch (ClassNotFoundException e) {
            System.err.println("failed to find class " + className);
        }

    }
}
