/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.util.scanner;

import org.hotswap.agent.logging.AgentLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scan classpath for a directory and visits each file.
 * <p/>
 * Thread context classloader is used to scan.
 *
 * @author Jiri Bubnik
 */
public class ClassPathScanner implements Scanner {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassPathScanner.class);

    // scan for files inside JAR file - e.g. jar:file:\J:\HotswapAgent\target\HotswapAgent-1.0.jar!\org\hotswap\agent\plugin
    public static final String JAR_URL_SEPARATOR = "!/";
    public static final String JAR_URL_PREFIX = "jar:";
    public static final String ZIP_URL_PREFIX = "zip:";
    public static final String FILE_URL_PREFIX = "file:";


    @Override
    public void scan(ClassLoader classLoader, String path, ScannerVisitor visitor) throws IOException {
        LOGGER.trace("Scanning path {}", path);
        // find all directories - classpath directory or JAR
        Enumeration<URL> en = classLoader == null ? ClassLoader.getSystemResources(path) : classLoader.getResources(path);
        while (en.hasMoreElements()) {
            URL pluginDirURL = en.nextElement();
            File pluginDir = new File(pluginDirURL.getFile());
            if (pluginDir.isDirectory()) {
                scanDirectory(pluginDir, visitor);
            } else {
                // JAR file
                String uri;
                try {
                    uri = pluginDirURL.toURI().toString();
                } catch (URISyntaxException e) {
                    throw new IOException("Illegal directory URI " + pluginDirURL, e);
                }

                if (uri.startsWith(JAR_URL_PREFIX) || uri.startsWith(ZIP_URL_PREFIX)) {
                    String jarFile = uri.substring(uri.indexOf(':') + 1); // remove the prefix
                    scanJar(jarFile, visitor);
                } else {
                    LOGGER.warning("Unknown resource type of file " + uri);
                }
            }
        }
    }

    /**
     * Recursively scan the directory.
     *
     * @param pluginDir directory.
     * @param visitor   callback
     * @throws IOException exception from a visitor
     */
    protected void scanDirectory(File pluginDir, ScannerVisitor visitor) throws IOException {
        LOGGER.trace("Scanning directory " + pluginDir.getName());

        for (File file : pluginDir.listFiles()) {
            if (file.isDirectory()) {
                scanDirectory(file, visitor);
            } else if (file.isFile() && file.getName().endsWith(".class")) {
                visitor.visit(new FileInputStream(file));
            }
        }
    }

    /**
     * Scan JAR file for all entries.
     * Resolve the JAR file itself and than iterate all entries and call visitor.
     *
     * @param urlFile URL to the file containing scanned directory
     *                (e.g. jar:file:\J:\HotswapAgent\target\HotswapAgent-1.0.jar!\org\hotswap\agent\plugin)
     * @param visitor callback
     * @throws IOException exception from a visitor
     */
    private void scanJar(String urlFile, ScannerVisitor visitor) throws IOException {
        LOGGER.trace("Scanning JAR file '{}'", urlFile);

        int separatorIndex = urlFile.indexOf(JAR_URL_SEPARATOR);
        JarFile jarFile = null;
        String rootEntryPath;

        try {
            if (separatorIndex != -1) {
                String jarFileUrl = urlFile.substring(0, separatorIndex);
                rootEntryPath = urlFile.substring(separatorIndex + JAR_URL_SEPARATOR.length());
                jarFile = getJarFile(jarFileUrl);
            } else {
                rootEntryPath = "";
                jarFile = new JarFile(urlFile);
            }

            if (!"".equals(rootEntryPath) && !rootEntryPath.endsWith("/")) {
                rootEntryPath = rootEntryPath + "/";
            }

            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                String entryPath = entry.getName();

                // class files inside entry
                if (entryPath.startsWith(rootEntryPath) && entryPath.endsWith(".class")) {
                    LOGGER.trace("Visiting JAR entry {}", entryPath);
                    visitor.visit(jarFile.getInputStream(entry));
                }
            }
        } finally {
            if (jarFile != null) {
                jarFile.close();
            }
        }
    }

    /**
     * Resolve the given jar file URL into a JarFile object.
     */
    protected JarFile getJarFile(String jarFileUrl) throws IOException {
        LOGGER.trace("Opening JAR file " + jarFileUrl);
        if (jarFileUrl.startsWith(FILE_URL_PREFIX)) {
            try {
                return new JarFile(toURI(jarFileUrl).getSchemeSpecificPart());
            } catch (URISyntaxException ex) {
                // Fallback for URLs that are not valid URIs (should hardly ever happen).
                return new JarFile(jarFileUrl.substring(FILE_URL_PREFIX.length()));
            }
        } else {
            return new JarFile(jarFileUrl);
        }
    }

    /**
     * Create a URI instance for the given location String,
     * replacing spaces with "%20" quotes first.
     *
     * @param location the location String to convert into a URI instance
     * @return the URI instance
     * @throws URISyntaxException if the location wasn't a valid URI
     */
    public static URI toURI(String location) throws URISyntaxException {
        return new URI(location.replace(" ", "%20"));
    }
}
