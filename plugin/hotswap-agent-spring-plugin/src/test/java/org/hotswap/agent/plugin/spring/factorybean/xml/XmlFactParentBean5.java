package org.hotswap.agent.plugin.spring.factorybean.xml;

import org.springframework.beans.factory.annotation.Autowired;

public class XmlFactParentBean5 {

    private XmlFactBean5 xmlFactBean5;

    @Autowired
    public XmlFactParentBean5(XmlFactBean5 xmlFactBean5) {
        this.xmlFactBean5 = xmlFactBean5;
    }

    public XmlFactBean5 getXmlFactBean5() {
        return xmlFactBean5;
    }

    public void setXmlFactBean5(XmlFactBean5 xmlFactBean5) {
        this.xmlFactBean5 = xmlFactBean5;
    }

}
