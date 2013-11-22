package org.hotswap.agent.config;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.classloader.URLClassLoaderHelper;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Plugin configuration.
 *
 * @author Jiri Bubnik
 */
public class PluginConfiguration {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PluginConfiguration.class);

    private static final String PLUGIN_CONFIGURATION = "hotswap-agent.properties";

    Properties properties = new Properties();

    PluginConfiguration parent;
    ClassLoader classLoader;
    URL configurationURL;


    public PluginConfiguration(ClassLoader classLoader) {
        this.classLoader = classLoader;
        configurationURL = classLoader.getResource(PLUGIN_CONFIGURATION);

        try {
            if (configurationURL != null) {
                properties.load(configurationURL.openStream());
                init();
            }
        } catch (Exception e) {
            LOGGER.error("Error while loading 'hotswap-agent.properties' from base URL " + configurationURL, e);
        }
    }

    public PluginConfiguration(PluginConfiguration parent, ClassLoader classLoader) {
        this.parent = parent;
        this.classLoader = classLoader;

        // search for resources not known by parent classloader (defined in THIS classloader exclusively)
        // this is necessary in case of parent classloader precedence
        try {
            Enumeration<URL> urls = classLoader.getResources(PLUGIN_CONFIGURATION);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();

                boolean found = false;

                if (parent != null) {
                    Enumeration<URL> parentUrls = parent.getClassLoader().getResources(PLUGIN_CONFIGURATION);
                    while (parentUrls.hasMoreElements()) {
                        if (url.equals(parentUrls.nextElement()))
                            found = true;
                    }
                }

                if (!found) {
                    configurationURL = url;
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error while loading 'hotswap-agent.properties' from URL " + configurationURL, e);
        }

        if (configurationURL == null) {
            configurationURL = parent.configurationURL;
        }

        try {
            properties.load(configurationURL.openStream());
            init();
        } catch (Exception e) {
            LOGGER.error("Error while loading 'hotswap-agent.properties' from URL " + configurationURL, e);
        }
    }

    /**
     * Initialize the configuration.
     */
    protected void init() {
        // LOG
        LogConfigurationHelper.configureLog(properties);

        // Extra class path
        URL[] extraClassPath = getExtraClasspath();
        if (extraClassPath.length > 0) {
            if (classLoader instanceof URLClassLoader) {
                URLClassLoaderHelper.prependClassPath((URLClassLoader)classLoader, extraClassPath);
            } else {
                LOGGER.warning("Unable to set extraClasspath to {} on classLoader {}. " +
                        "Only URLClassLoader is supported.", Arrays.toString(extraClassPath), classLoader);
            }
        }
    }

    public String getProperty(String property) {
        if (properties.containsKey(property))
            return properties.getProperty(property);
        else if (parent != null)
            return parent.getProperty(property);
        else
            return null;
    }

    public URL[] getExtraClasspath() {
        return convertToURL(getProperty("extraClasspath"));
    }

    /**
     * Converts watchResources property to URL array. Invalid URLs will be skipped and logged as error.
     */
    public URL[] getWatchResources() {
        return convertToURL(getProperty("watchResources"));
    }

    private URL[] convertToURL(String resources) {
        List<URL> ret = new ArrayList<URL>();

        if (resources != null) {
            StringTokenizer tokenizer = new StringTokenizer(resources, ",;");
            while (tokenizer.hasMoreTokens()) {
                String name = tokenizer.nextToken().trim();
                try {
                    ret.add(resourceNameToURL(name));
                } catch (Exception e) {
                    LOGGER.error("Invalid configuration value: '{}' is not a valid URL or path and will be skipped.", name, e);
                }
            }
        }

        return ret.toArray(new URL[ret.size()]);
    }

    private static URL resourceNameToURL(String resource) throws Exception {
        try {
            // Try to format as a URL?
            return new URL(resource);
        } catch (MalformedURLException e) {
            // try to locate a file
            if (resource.startsWith("./"))
                resource = resource.substring(2);

            File file = new File(resource).getCanonicalFile();
            return file.toURI().toURL();
        }
    }

    /**
     * Returns classloader associated with this configuration (i.e. it was initiated from).
     *
     * @return the classloader
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
