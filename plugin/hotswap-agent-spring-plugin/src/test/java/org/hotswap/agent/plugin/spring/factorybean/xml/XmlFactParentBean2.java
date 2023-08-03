package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.beans.factory.annotation.Autowired;

public class XmlFactParentBean2 {

    @Autowired
    private XmlFactBean2 xmlFactBean2;

    public XmlFactBean2 getXmlFactBean2() {
        return xmlFactBean2;
    }

    public void setXmlFactBean2(XmlFactBean2 xmlFactBean2) {
        this.xmlFactBean2 = xmlFactBean2;
    }
}
