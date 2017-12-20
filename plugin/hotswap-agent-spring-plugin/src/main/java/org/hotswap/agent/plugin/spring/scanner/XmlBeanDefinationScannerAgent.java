package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.ResetBeanPostProcessorCaches;
import org.hotswap.agent.plugin.spring.SpringPlugin;
import org.hotswap.agent.plugin.spring.getbean.ProxyReplacer;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * IMPORTANT: DON'T REFER TO THIS CLASS IN OTHER CLASS!!
 */
public class XmlBeanDefinationScannerAgent {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlBeanDefinationScannerAgent.class);

    private static Map<String, XmlBeanDefinationScannerAgent> instances = new HashMap<String, XmlBeanDefinationScannerAgent>();

    // xmlReader for corresponding url
    BeanDefinitionReader reader;

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;


    /**
     * need to ensure that when method is invoked first time , this class is not loaded,
     * so this class is will be loaded by appClassLoader
     */
    public static void registerXmlBeanDefinationScannerAgent(XmlBeanDefinitionReader reader, Resource resource) {
        String path;
        if (resource instanceof ClassPathResource) {
            path = ((ClassPathResource)resource).getPath();
        } else {
            try {
                path = convertToClasspathURL(resource.getURL().getPath());
            } catch (IOException e) {
                e.printStackTrace();
                LOGGER.error("Cannot get url from resource: {}", resource);
                return;
            }
        }

        instances.put(path, new XmlBeanDefinationScannerAgent(reader));
    }

    public static void reloadXml(URL url) {
        XmlBeanDefinationScannerAgent xmlBeanDefinationScannerAgent = instances.get(convertToClasspathURL(url.getPath()));
        if (xmlBeanDefinationScannerAgent == null) {
            LOGGER.warning("url " + url + " is not associated with any XmlBeanDefinationScannerAgent, not reloading");
            return;
        }
        xmlBeanDefinationScannerAgent.reloadBeanFromXml(url);
    }

    private static boolean basePackageInited = false;

    private XmlBeanDefinationScannerAgent(BeanDefinitionReader reader) {
        this.reader = reader;

        if (SpringPlugin.basePackagePrefixes != null && !basePackageInited) {
            ClassPathBeanDefinitionScannerAgent xmlBeanDefinitionScannerAgent = ClassPathBeanDefinitionScannerAgent.getInstance(new ClassPathBeanDefinitionScanner(reader.getRegistry()));
            for (String basePackage : SpringPlugin.basePackagePrefixes) {
                xmlBeanDefinitionScannerAgent.registerBasePackage(basePackage);
            }
            basePackageInited = true;
        }
    }

    /**
     * convert src/main/resources/xxx.xml and classes/xxx.xml to xxx.xml
     * @param filePath the file path to convert
     * @return if convert succeed, return classpath path, or else return file path
     */
    private static String convertToClasspathURL(String filePath) {
        String[] paths = filePath.split("src/main/resources/");
        if (paths.length == 2) {
            return paths[1];
        }

        paths = filePath.split("WEB-INF/classes/");
        if (paths.length == 2) {
            return paths[1];
        }

        paths = filePath.split("target/classes/");
        if (paths.length == 2) {
            return paths[1];
        }

        paths = filePath.split("target/test-classes/");
        if (paths.length == 2) {
            return paths[1];
        }

        LOGGER.error("failed to convert filePath {} to classPath path", filePath);
        return filePath;
    }

    /**
     *  reload bean from xml defination
     *  @param url url of xml
     */
    public void reloadBeanFromXml(URL url) {
        LOGGER.info("reloading xml file: " + url);
        // this will call registerBeanDefinition which in turn call resetBeanDefinition to destroy singleton
        // maybe should use watchResourceClassLoader.getResource?
        this.reader.loadBeanDefinitions(new FileSystemResource(url.getPath()));
        // spring won't rebuild dependency map if injectionMetadataCache is not cleared
        // which lead to singletons depend on beans in xml won't be destroy and recreate, may be a spring bug?
        ResetBeanPostProcessorCaches.reset(maybeRegistryToBeanFactory());
        ProxyReplacer.clearAllProxies();
        reloadFlag = false;
    }

    private DefaultListableBeanFactory maybeRegistryToBeanFactory() {
        BeanDefinitionRegistry registry = this.reader.getRegistry();
        if (registry instanceof DefaultListableBeanFactory) {
            return (DefaultListableBeanFactory) registry;
        } else if (registry instanceof GenericApplicationContext) {
            return ((GenericApplicationContext) registry).getDefaultListableBeanFactory();
        }
        return null;
    }
}
