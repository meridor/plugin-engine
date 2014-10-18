package ru.meridor.stecker.impl;

import ru.meridor.stecker.ClassesScanner;
import ru.meridor.stecker.Plugin;
import ru.meridor.stecker.PluginException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class DefaultClassesScanner implements ClassesScanner {

    public static final String LIB_DIRECTORY = "lib";
    public static final String PLUGIN_CLASSES_FILE = "plugin.jar";
    private static final String JAR_FILE_EXTENSION = ".jar";
    private static final String CLASS_FILE_EXTENSION = ".class";
    private static final String JAR_DIRECTORY_SEPARATOR = "/";
    private static final String PACKAGE_SEPARATOR = ".";
    private final Path cacheDirectory;

    public DefaultClassesScanner(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    public Map<Class, List<Class>> scan(Path pluginFile, List<Class> extensionPoints) throws PluginException {
        try {
            extensionPoints.add(Plugin.class); //Plugin base class is always an extension point
            Path unpackedPluginDirectory = unpackPlugin(pluginFile, cacheDirectory);
            Path pluginJarFile = getPluginJarPath(unpackedPluginDirectory);
            ClassLoader classLoader = getClassLoader(unpackedPluginDirectory);
            return getMatchingClasses(extensionPoints, pluginJarFile, classLoader);

        } catch (Throwable e) {
            throw new PluginException(e);
        }
    }

    private Path unpackPlugin(Path pluginFile, Path cacheDirectory) throws IOException {
        String pluginName = pluginFile.getFileName().toString().replace(JAR_FILE_EXTENSION, "");
        Path pluginStorageDirectory = cacheDirectory.resolve(pluginName);

        if (Files.exists(pluginStorageDirectory)) {
            if (!Files.isDirectory(pluginStorageDirectory)) {
                throw new IOException("Plugin cache directory is not a directory");
            }
            if (fileIsNewerThan(pluginStorageDirectory, pluginFile)) {
                return pluginStorageDirectory;
            }
            FileSystemHelper.removeDirectory(pluginStorageDirectory);
        }

        Files.createDirectories(pluginStorageDirectory);

        unpackJar(pluginFile, pluginStorageDirectory);

        return pluginStorageDirectory;
    }

    private void unpackJar(Path pluginFile, Path pluginStorageDirectory) throws IOException {

        try (JarFile jarFile = new JarFile(pluginFile.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path outputPath = Paths.get(pluginStorageDirectory.toUri()).resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                    continue;
                }

                Path parentDirectory = outputPath.getParent();
                if (!Files.exists(parentDirectory)) {
                    Files.createDirectories(parentDirectory);
                }

                try (
                        InputStream is = jarFile.getInputStream(entry);
                        OutputStream os = Files.newOutputStream(outputPath)
                ) {
                    while (is.available() > 0) {
                        os.write(is.read());
                    }
                }

            }
        }
    }

    private boolean fileIsNewerThan(Path file, Path anotherFile) throws IOException {
        FileTime fileLastModificationTime = Files.readAttributes(file, BasicFileAttributes.class).lastModifiedTime();
        FileTime anotherFileLastModificationTime = Files.readAttributes(anotherFile, BasicFileAttributes.class).lastModifiedTime();
        return fileLastModificationTime.compareTo(anotherFileLastModificationTime) > 0;
    }

    private Path getPluginJarPath(Path unpackedPluginDirectory) {
        return unpackedPluginDirectory.resolve(PLUGIN_CLASSES_FILE);
    }

    private ClassLoader getClassLoader(Path unpackedPluginDirectory) throws PluginException {
        try {
            Path libDirectory = unpackedPluginDirectory.resolve(LIB_DIRECTORY);
            List<URL> urls = new ArrayList<>();
            if (Files.exists(libDirectory) && Files.isDirectory(libDirectory)) {
                List<URI> uris = Files.list(libDirectory)
                        .filter(Files::isRegularFile)
                        .map(Path::toUri)
                        .collect(Collectors.toList());
                for (URI uri : uris) {
                    urls.add(uri.toURL());
                }
            }
            Path pluginJarPath = getPluginJarPath(unpackedPluginDirectory);
            urls.add(pluginJarPath.toUri().toURL());
            return new URLClassLoader(urls.toArray(new URL[urls.size()]));
        } catch (IOException e) {
            throw new PluginException(e);
        }
    }

    private Map<Class, List<Class>> getMatchingClasses(List<Class> extensionPoints, Path pluginJarFile, ClassLoader classLoader) throws Exception {
        Map<Class, List<Class>> matchingClasses = new HashMap<>();
        JarFile jar = new JarFile(pluginJarFile.toFile());
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(CLASS_FILE_EXTENSION) && !entry.isDirectory()) {
                String className = entry.getName()
                        .replace(JAR_DIRECTORY_SEPARATOR, PACKAGE_SEPARATOR)
                        .replace(CLASS_FILE_EXTENSION, "");
                if (className.startsWith(PACKAGE_SEPARATOR) && className.length() > 1) {
                    className = className.substring(1);
                }
                Class<?> currentClass = Class.forName(className, true, classLoader);
                for (Class<?> extensionPoint : extensionPoints) {
                    if (extensionPoint.isAssignableFrom(currentClass)) {
                        if (!matchingClasses.containsKey(extensionPoint)) {
                            matchingClasses.put(extensionPoint, new ArrayList<>());
                        }
                        matchingClasses.get(extensionPoint).add(currentClass);
                    }
                }
            }
        }
        return matchingClasses;
    }

}