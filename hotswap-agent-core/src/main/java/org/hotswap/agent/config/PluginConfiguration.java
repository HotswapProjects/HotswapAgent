package org.hotswap.agent.config;

import org.hotswap.agent.HotswapAgent;
import org.hotswap.agent.annotation.Plugin;
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
 * <p/>
 * Single instance exists for each classloader.
 *
 * @author Jiri Bubnik
 */
public class PluginConfiguration {
    private static AgentLogger LOGGER = AgentLogger.getLogger(PluginConfiguration.class);

    private static final String PLUGIN_CONFIGURATION = "hotswap-agent.properties";

    Properties properties = new Properties();

    // if the property is not defined in this classloader, look for parent classloader and it's configuration
    PluginConfiguration parent;

    // this configuration adheres to this classloader
    ClassLoader classLoader;

    // the hotswap-agent.properties file (or null if not defined for this classloader)
    URL configurationURL;

    // is property file defined directly in this classloader?
    boolean containsPropertyFileDirectly = false;


    public PluginConfiguration(ClassLoader classLoader) {
        this.classLoader = classLoader;
        configurationURL = classLoader.getResource(PLUGIN_CONFIGURATION);

        try {
            if (configurationURL != null) {
                containsPropertyFileDirectly = true;
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

        if (configurationURL == null && parent != null) {
            configurationURL = parent.configurationURL;
            LOGGER.debug("Classloader does not contain 'hotswap-agent.properties', using parent file '{}'", parent.configurationURL);
        } else {
            LOGGER.debug("Classloader contains 'hotswap-agent.properties' at location '{}'", configurationURL);
            containsPropertyFileDirectly = true;
        }

        try {
            if (configurationURL != null)
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
        LogConfigurationHelper.configureLog(properties);

        initPluginPackage();

        initExtraClassPath();
    }

    private void initPluginPackage() {
        // only for self property (not parent)
        if (properties.containsKey("pluginPackages")) {
            String pluginPackages = properties.getProperty("pluginPackages");

            for (String pluginPackage : pluginPackages.split(",")) {
                PluginManager.getInstance().getPluginRegistry().scanPlugins(getClassLoader(), pluginPackage);
            }
        }
    }

    private void initExtraClassPath() {
        URL[] extraClassPath = getExtraClasspath();
        if (extraClassPath.length > 0) {
            if (classLoader instanceof URLClassLoader) {
                URLClassLoaderHelper.prependClassPath((URLClassLoader) classLoader, extraClassPath);
            } else {
                LOGGER.debug("Unable to set extraClasspath to {} on classLoader {}. " +
                        "Only URLClassLoader is supported.\n" +
                        "*** extraClasspath configuration property will not be handled on JVM level ***", Arrays.toString(extraClassPath), classLoader);
            }
        }
    }

    /**
     * Get configuration property value
     *
     * @param property property name
     * @return the property value or null if not defined
     */
    public String getProperty(String property) {
        if (properties.containsKey(property))
            return properties.getProperty(property);
        else if (parent != null)
            return parent.getProperty(property);
        else
            return null;
    }

    /**
     * Get configuration property value
     *
     * @param property property name
     * @param defaultValue value to return if property not defined
     * @return the property value or null if not defined
     */
    public String getProperty(String property, String defaultValue) {
        String value = getProperty(property);
        return value != null ? value : defaultValue;
    }

    /**
     * Convenience method to get property as a boolean value using Boolean.valueOf().
     *
     * @param property property name
     * @return the property value or null if not defined
     */
    public boolean getPropertyBoolean(String property) {
        if (properties.containsKey(property))
            return Boolean.valueOf(properties.getProperty(property));
        else if (parent != null)
            return parent.getPropertyBoolean(property);
        else
            return false;
    }

    /**
     * Get extraClasspath property as URL[].
     *
     * @return extraClasspath or empty array (never null)
     */
    public URL[] getExtraClasspath() {
        return convertToURL(getProperty("extraClasspath"));
    }

    /**
     * Converts watchResources property to URL array. Invalid URLs will be skipped and logged as error.
     */
    public URL[] getWatchResources() {
        return convertToURL(getProperty("watchResources"));
    }

    /**
     * Return configuration property webappDir as URL.
     */
    public URL getWebappDir() {
        try {
            String webappDir = getProperty("webappDir");
            if (webappDir != null && webappDir.length() > 0) {
                return resourceNameToURL(webappDir);
            } else {
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Invalid configuration value for webappDir: '{}' is not a valid URL or path and will be skipped.", getProperty("webappDir"), e);
            return null;
        }
    }

    /**
     * List of disabled plugin names
     */
    public List<String> getDisabledPlugins() {
        List<String> ret = new ArrayList<String>();
        for (String disabledPlugin : getProperty("disabledPlugins", "").split(",")) {
            ret.add(disabledPlugin.trim());
        }
        return ret;
    }

    /**
     * Check if the plugin is disabled (in this classloader)
     */
    public boolean isDisabledPlugin(String pluginName) {
        return HotswapAgent.isPluginDisabled(pluginName) || getDisabledPlugins().contains(pluginName);
    }

    /**
     * Check if the plugin is disabled (in this classloader)
     */
    public boolean isDisabledPlugin(Class<Object> pluginClass) {
        Plugin pluginAnnotation = pluginClass.getAnnotation(Plugin.class);
        return isDisabledPlugin(pluginAnnotation.name());
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

    /**
     * Does this classloader contain the property file directly, or is it acquired through parent classloader.
     *
     * @return if this contains directly the property file
     */
    public boolean containsPropertyFile() {
        return containsPropertyFileDirectly;
    }
}
