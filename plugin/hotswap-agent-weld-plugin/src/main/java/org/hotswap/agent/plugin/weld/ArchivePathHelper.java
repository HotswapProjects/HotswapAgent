package org.hotswap.agent.plugin.weld;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;

public class ArchivePathHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ArchivePathHelper.class);

    public static String getArchivePath(CtClass ctClass) throws NotFoundException {
        String classFilePath = ctClass.getURL().getPath();
        String className = ctClass.getName().replace(".", "/");
        // archive path ends with '/' therefore we set end position before the '/' (-1)
        String archivePath = classFilePath.substring(0, classFilePath.indexOf(className) - 1);
        return (new File(archivePath)).toPath().toString();
    }

    /**
     * Method resolves archive path from BdaId
     *
     * @param classLoader the class loader
     * @param archiveId the archive id
     * @return the normalized archive path
     */
    public static String getNormalizedArchivePath(ClassLoader classLoader, String archiveId) {
        String result = null;
        URL archiveURL = archivePathToURL(classLoader, archiveId);
        if (archiveURL != null) {
            URI archiveURI;
            try {
                archiveURI = archiveURL.toURI();
                if (IOUtils.isFileURL(archiveURI.toURL())) {
                    result = archiveURL.getFile();
                    // Strip trailing "/" from normalized archive path
                    while (result.endsWith("/")) {
                        result = result.substring(0, result.length() -1);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("getNormalizedArchivePath() exception {}.", e.getMessage());
            }
        }
        return result;
    }

    private static URL archivePathToURL(ClassLoader classLoader, String archiveId) {
        URL result = archiveFilePathToURL(archiveId);
        if (result == null) {
            String archivePath = archiveId;
            // File doesn't exists, try to resolve it using appClassLoader
            if (classLoader instanceof URLClassLoader) {
                result = archivePathToURLViaURLClassLoader((URLClassLoader) classLoader, archivePath);
            }
        }
        return result;
    }

    private static URL archivePathToURLViaURLClassLoader(URLClassLoader urlClassLoader, String archivePath) {
        URL[] urls = urlClassLoader.getURLs();
        if (urls != null) {
            for (URL url: urls) {
                String fileName = url.getFile();
                String checkedArchivePath = fileName.endsWith("/") && !archivePath.endsWith("/") ? archivePath + "/" : archivePath;
                if (fileName.endsWith(checkedArchivePath)) {
                    return archiveFilePathToURL(fileName);
                }
            }
        }
        return null;
    }

    private static URL archiveFilePathToURL(String archivePath) {
        File f = new File(archivePath);
        if (f.exists()) {
            try {
                try {
                    // Try to format as a URL?
                    return f.toURI().toURL();
                } catch (MalformedURLException e) {
                    // try to locate a file
                    if (archivePath.startsWith("./"))
                        archivePath = archivePath.substring(2);
                    File file = new File(archivePath).getCanonicalFile();
                    return file.toURI().toURL();
                }
            } catch (Exception e) {
                // Swallow exception
            }
        }
        return null;
    }

}
