package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class XmlConstructorParentBeanMul2 {

    @Autowired
    public XmlConstructorParentBeanMul2(XmlConstructorBean1 xmlConstructorBean1, XmlConstructorBean2 xmlConstructorBean2) {
        this.xmlConstructorBean1 = xmlConstructorBean1;
        this.xmlConstructorBean2 = xmlConstructorBean2;
    }

    private XmlConstructorBean1 xmlConstructorBean1;
    private XmlConstructorBean2 xmlConstructorBean2;
    @Autowired
    private XmlConstructorBean3 xmlConstructorBean3;

    public XmlConstructorBean1 getXmlConstructorBean1() {
        return xmlConstructorBean1;
    }

    public XmlConstructorBean2 getXmlConstructorBean2() {
        return xmlConstructorBean2;
    }

    public XmlConstructorBean3 getXmlConstructorBean3() {
        return xmlConstructorBean3;
    }

}
