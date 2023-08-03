package org.hotswap.agent.plugin.spring.xml.bak.constructor.v2;

import org.hotswap.agent.plugin.spring.xml.constructor.XmlConstructorBean1;
import org.hotswap.agent.plugin.spring.xml.constructor.XmlConstructorBean3;
import org.hotswap.agent.plugin.spring.xml.constructor.XmlConstructorBean4;
import org.hotswap.agent.plugin.spring.xml.constructor.XmlConstructorBean5;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BakConstructorParentBeanMul1 {

    public BakConstructorParentBeanMul1(XmlConstructorBean3 xmlConstructorBean3, XmlConstructorBean5 xmlConstructorBean5) {
        this.xmlConstructorBean3 = xmlConstructorBean3;
        this.xmlConstructorBean5 = xmlConstructorBean5;
    }

    private XmlConstructorBean3 xmlConstructorBean3;
    private XmlConstructorBean5 xmlConstructorBean5;
    @Autowired
    private XmlConstructorBean1 xmlConstructorBean1;
    @Autowired
    private XmlConstructorBean4 xmlConstructorBean4;

    public XmlConstructorBean3 getXmlConstructorBean3() {
        return xmlConstructorBean3;
    }

    public XmlConstructorBean1 getXmlConstructorBean1() {
        return xmlConstructorBean1;
    }

    public XmlConstructorBean4 getXmlConstructorBean4() {
        return xmlConstructorBean4;
    }

    public XmlConstructorBean5 getXmlConstructorBean5() {
        return xmlConstructorBean5;
    }
}
