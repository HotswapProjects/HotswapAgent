package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.beans.factory.annotation.Autowired;

public class XmlConstructorParentBean3 {

    private XmlConstructorBean3 xmlConstructorBean3;

    @Autowired
    public XmlConstructorParentBean3(XmlConstructorBean3 xmlConstructorBean3) {
        this.xmlConstructorBean3 = xmlConstructorBean3;
    }

    public XmlConstructorBean3 getXmlConstructorBean3() {
        return xmlConstructorBean3;
    }

    public void setXmlConstructorBean3(XmlConstructorBean3 xmlConstructorBean3) {
        this.xmlConstructorBean3 = xmlConstructorBean3;
    }
}
