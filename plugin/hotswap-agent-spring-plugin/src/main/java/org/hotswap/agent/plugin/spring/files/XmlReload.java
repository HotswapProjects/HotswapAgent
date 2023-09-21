package org.hotswap.agent.plugin.spring.files;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XmlReload {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlReload.class);

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
        synchronized (xmls) {
            Set<String> xmlResourcePaths = new HashSet<>();
            if (propertiesChanged) {
                xmlResourcePaths.addAll(placeHolderXmlRelation.values());
            }
            Set<String> result = XmlBeanDefinitionScannerAgent.reloadXmls(beanFactory, xmls, xmlResourcePaths);
            // clear the xmls after the beanDefinition is refreshed.
            xmls.clear();
            return result;
        }
    }
}
