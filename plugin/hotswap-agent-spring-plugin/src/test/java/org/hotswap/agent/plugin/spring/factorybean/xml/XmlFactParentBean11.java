package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.stereotype.Component;

@Component
public class XmlFactParentBean11 {

    public XmlFactParentBean11(XmlFactBean1 xmlFactBean1) {
        this.xmlFactBean1 = xmlFactBean1;
    }

    private XmlFactBean1 xmlFactBean1;

    public XmlFactBean1 getXmlFactBean1() {
        return xmlFactBean1;
    }

}
