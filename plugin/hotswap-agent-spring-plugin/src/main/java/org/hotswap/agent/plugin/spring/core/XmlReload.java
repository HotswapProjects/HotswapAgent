package org.hotswap.agent.plugin.spring.core;

import org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XmlReload {

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
    public static Set<String> reloadXmlsAndGetBean(boolean propertiesChanged, Map<String, String> placeHolderXmlRelation,
                                            Set<String> recreateBean, Set<URL> xmls) {
        Set<String> xmlResourcePaths = new HashSet<>();
        if (propertiesChanged) {
            xmlResourcePaths.addAll(placeHolderXmlRelation.values());
        }
//        else {
//            for (String beanName : recreateBean) {
//                String value = placeHolderXmlRelation.get(beanName);
//                if (value != null) {
//                    xmlResourcePaths.add(value);
//                }
//            }
//        }
        return XmlBeanDefinitionScannerAgent.reloadXmls(xmls, xmlResourcePaths);
    }
}
