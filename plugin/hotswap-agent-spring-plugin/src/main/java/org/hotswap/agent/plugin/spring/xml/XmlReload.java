package org.hotswap.agent.plugin.spring.xml;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.SpringReload;
import org.hotswap.agent.plugin.spring.xml.XmlBeanDefinitionScannerAgent;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XmlReload {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlReload.class);
    public static void setClassLoader(ClassLoader classLoader) {
        XmlBeanDefinitionScannerAgent.setAppClassLoader(classLoader);
    }

    /**
     * if no properties changed, check destroyed beans and the related xmls.
     * if properties changed, make sure all xmls are reloaded.
     *
     * @param propertiesChanged
     * @param placeHolderXmlRelation
     * @param recreateBean
     * @param xmls
     * @return
     */
    public static Set<String> reloadXmlsAndGetBean(DefaultListableBeanFactory beanFactory, boolean propertiesChanged,
                                                   Map<String, String> placeHolderXmlRelation,Set<String> recreateBean, Set<URL> xmls) {
        LOGGER.debug("reloadXmlsAndGetBean, propertiesChanged: {}, placeHolderXmlRelation: {}, recreateBean: {}, xmls: {}",
                propertiesChanged, placeHolderXmlRelation, recreateBean, xmls);
        Set<String> xmlResourcePaths = new HashSet<>();
        if (propertiesChanged) {
            xmlResourcePaths.addAll(placeHolderXmlRelation.values());
        }
        return XmlBeanDefinitionScannerAgent.reloadXmls(beanFactory, xmls, xmlResourcePaths);
    }
}
