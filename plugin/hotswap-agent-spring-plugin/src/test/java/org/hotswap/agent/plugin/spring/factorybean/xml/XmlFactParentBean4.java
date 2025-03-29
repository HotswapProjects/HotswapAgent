package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.beans.factory.annotation.Autowired;

public class XmlFactParentBean4 {

    private XmlFactBean4 xmlFactBean4;

    @Autowired
    public XmlFactParentBean4(XmlFactBean4 xmlFactBean4) {
        this.xmlFactBean4 = xmlFactBean4;
    }

    public XmlFactBean4 getXmlFactBean4() {
        return xmlFactBean4;
    }

}
