/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.mojarra;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Directories that HotswapAgent serves on top of the web application (webappDir, watchResources and
 * extraClasspath from hotswap-agent.properties).
 * <p>
 * Facelets from these directories have file: URLs outside of the deployment, which Mojarra 4.0.27+ / 4.1.13+
 * rejects for relative includes and templates as "not within the application root", see #684.
 */
public class ExtraWebappRoots {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(ExtraWebappRoots.class);

    private static final String[] NONE = new String[0];

    private ExtraWebappRoots() {
    }

    /**
     * Resolves the canonical paths of the configured directories, each ending with the file separator.
     *
     * @param classLoader web application class loader
     * @return directories, never null
     */
    public static String[] resolve(ClassLoader classLoader) {
        try {
            // init=false: only read the configuration, do not apply it to the class loader again
            PluginConfiguration configuration = new PluginConfiguration(null, classLoader, false);
            List<String> roots = new ArrayList<>();
            addRoots(roots, configuration.getWebappDir());
            addRoots(roots, configuration.getWatchResources());
            addRoots(roots, configuration.getExtraClasspath());
            LOGGER.debug("Extra webapp roots for {}: {}", classLoader, roots);
            return roots.toArray(NONE);
        } catch (Exception e) {
            LOGGER.warning("Unable to resolve extra webapp roots for {}", e, classLoader);
            return NONE;
        }
    }

    /**
     * @param roots directories from {@link #resolve(ClassLoader)}
     * @param url resolved facelet url
     * @return true if the url is a file inside one of the directories
     */
    public static boolean contains(String[] roots, URL url) {
        return findRoot(roots, url) != null;
    }

    /**
     * Path of the url in the web application, the same format as Mojarra's DefaultFaceletFactory.getWebappPath:
     * relative to the web application root, without the leading slash, e.g. "contracts/c1/template.xhtml".
     * Each directory is served at the web application root, so the path is relative to the directory.
     *
     * @param roots directories from {@link #resolve(ClassLoader)}
     * @param url resolved facelet url
     * @return the path, or null if the url is not inside one of the directories
     */
    public static String webappPath(String[] roots, URL url) {
        String[] match = findRoot(roots, url);
        if (match == null) {
            return null;
        }
        return match[1].substring(match[0].length()).replace(File.separatorChar, '/');
    }

    /**
     * @return {root, canonical path of the url} for the longest (most specific) root containing the url, or null
     */
    private static String[] findRoot(String[] roots, URL url) {
        if (roots == null || roots.length == 0 || url == null || !"file".equals(url.getProtocol())) {
            return null;
        }
        try {
            String path = new File(url.toURI()).getCanonicalPath();
            String best = null;
            for (String root : roots) {
                if (path.startsWith(root) && (best == null || root.length() > best.length())) {
                    best = root;
                }
            }
            return best == null ? null : new String[] { best, path };
        } catch (Exception e) {
            LOGGER.debug("Unable to check url {}", e, url);
            return null;
        }
    }

    private static void addRoots(List<String> roots, URL[] urls) {
        for (URL url : urls) {
            if (!"file".equals(url.getProtocol())) {
                continue;
            }
            try {
                File dir = new File(url.toURI()).getCanonicalFile();
                if (dir.isDirectory()) {
                    roots.add(dir.getPath() + File.separator);
                }
            } catch (Exception e) {
                LOGGER.debug("Skipping {}", e, url);
            }
        }
    }
}
