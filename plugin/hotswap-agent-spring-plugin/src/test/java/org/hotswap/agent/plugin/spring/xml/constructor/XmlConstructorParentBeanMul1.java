package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class XmlConstructorParentBeanMul1 {

    @Autowired
    public XmlConstructorParentBeanMul1(XmlConstructorBean3 xmlConstructorBean3) {
        this.xmlConstructorBean3 = xmlConstructorBean3;
    }

    private XmlConstructorBean3 xmlConstructorBean3;

    public XmlConstructorBean3 getXmlConstructorBean3() {
        return xmlConstructorBean3;
    }

    public XmlConstructorBean1 getXmlConstructorBean1() {
        return null;
    }

    public XmlConstructorBean4 getXmlConstructorBean4() {
        return null;
    }

    public XmlConstructorBean5 getXmlConstructorBean5() {
        return null;
    }
}
