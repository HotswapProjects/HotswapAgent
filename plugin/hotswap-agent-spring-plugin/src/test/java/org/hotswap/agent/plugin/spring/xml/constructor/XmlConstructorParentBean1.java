package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class XmlConstructorParentBean1 {

    @Autowired
    private XmlConstructorBean1 xmlConstructorBean1;

    public XmlConstructorBean1 getXmlConstructorBean1() {
        return xmlConstructorBean1;
    }

    public void setXmlConstructorBean1(XmlConstructorBean1 xmlConstructorBean1) {
        this.xmlConstructorBean1 = xmlConstructorBean1;
    }
}
