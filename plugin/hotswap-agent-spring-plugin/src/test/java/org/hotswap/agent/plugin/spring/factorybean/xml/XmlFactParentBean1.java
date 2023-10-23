package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class XmlFactParentBean1 {

    @Autowired
    private XmlFactBean1 xmlFactBean1;

    public XmlFactBean1 getXmlFactBean1() {
        return xmlFactBean1;
    }

    public void setXmlFactBean1(XmlFactBean1 xmlFactBean1) {
        this.xmlFactBean1 = xmlFactBean1;
    }
}
