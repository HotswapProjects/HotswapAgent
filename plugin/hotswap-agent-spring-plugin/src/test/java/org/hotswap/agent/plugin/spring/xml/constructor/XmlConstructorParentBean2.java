package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.beans.factory.annotation.Autowired;

public class XmlConstructorParentBean2 {

    @Autowired
    private XmlConstructorBean2 xmlConstructorBean2;

    public XmlConstructorBean2 getXmlConstructorBean2() {
        return xmlConstructorBean2;
    }

    public void setXmlConstructorBean2(XmlConstructorBean2 xmlConstructorBean2) {
        this.xmlConstructorBean2 = xmlConstructorBean2;
    }
}
