package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class XmlBeanDefinationScannerAgent {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlBeanDefinationScannerAgent.class);

    private static Map<URL, XmlBeanDefinationScannerAgent> instances = new HashMap<URL, XmlBeanDefinationScannerAgent>();

    // xmlReader for corresponding url
    BeanDefinitionReader reader;

    /**
     * Flag to check reload status.
     * In unit test we need to wait for reload finish before the test can continue. Set flag to true
     * in the test class and wait until the flag is false again.
     */
    public static boolean reloadFlag = false;

    public static void registerXmlBeanDefinationScannerAgent(XmlBeanDefinitionReader reader, URL url) {
        instances.put(url, new XmlBeanDefinationScannerAgent(reader));
    }

    public static void reloadXml(URL url) {
        if (!url.getPath().endsWith(".xml")) {
            LOGGER.error(url + " is not xml");
            return;
        }

        // TODO: use classpath url as key, now xmlBeanDefinationScannerAgent is always null.
        XmlBeanDefinationScannerAgent xmlBeanDefinationScannerAgent = instances.get(url);
        if (xmlBeanDefinationScannerAgent == null) {
            LOGGER.warning("url " + url + " is not associated with any XmlBeanDefinationScannerAgent, using random reader");
            if (!instances.isEmpty()) {
                xmlBeanDefinationScannerAgent = ((XmlBeanDefinationScannerAgent)instances.values().toArray()[0]);
            } else {
                LOGGER.error("can't load xml file " + url + " because there is no xml loaded by spring before");
            }
        }
        xmlBeanDefinationScannerAgent.reloadBeanFromXml(url);
    }

    private XmlBeanDefinationScannerAgent(BeanDefinitionReader reader) {
        this.reader = reader;
    }

    /**
     *  reload bean from xml defination
     *  @param url url of xml
     */
    public void reloadBeanFromXml(URL url) {
        LOGGER.info("reloading xml file: " + url);
        this.reader.loadBeanDefinitions(new FileSystemResource(url.getPath()));
        // no need to clear cache
    }
}
