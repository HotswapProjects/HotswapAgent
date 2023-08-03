package org.hotswap.agent.plugin.spring.factorybean.xml;

public class XmlFactParentBean3 {

    private XmlFactBean3 xmlFactBean3;

    public XmlFactParentBean3(XmlFactBean3 xmlFactBean3) {
        this.xmlFactBean3 = xmlFactBean3;
    }

    public XmlFactBean3 getXmlFactBean3() {
        return xmlFactBean3;
    }

    public void setXmlFactBean3(XmlFactBean3 xmlFactBean3) {
        this.xmlFactBean3 = xmlFactBean3;
    }
}
