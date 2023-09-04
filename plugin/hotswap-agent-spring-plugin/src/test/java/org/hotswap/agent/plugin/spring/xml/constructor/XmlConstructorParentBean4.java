package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.beans.factory.annotation.Autowired;

public class XmlConstructorParentBean4 {

    private XmlConstructorBean4 xmlConstructorBean4;

    @Autowired
    public XmlConstructorBean4 getXmlConstructorBean4() {
        return xmlConstructorBean4;
    }

    public void setXmlConstructorBean4(XmlConstructorBean4 xmlConstructorBean4) {
        this.xmlConstructorBean4 = xmlConstructorBean4;
    }
}
