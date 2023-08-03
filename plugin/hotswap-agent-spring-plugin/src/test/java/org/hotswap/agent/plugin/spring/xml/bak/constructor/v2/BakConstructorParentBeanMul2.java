package org.hotswap.agent.plugin.spring.xml.bak.constructor.v2;

import org.hotswap.agent.plugin.spring.xml.constructor.XmlConstructorBean1;
import org.hotswap.agent.plugin.spring.xml.constructor.XmlConstructorBean2;
import org.hotswap.agent.plugin.spring.xml.constructor.XmlConstructorBean3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BakConstructorParentBeanMul2 {

    public BakConstructorParentBeanMul2(XmlConstructorBean1 xmlConstructorBean1) {
        this.xmlConstructorBean1 = xmlConstructorBean1;
    }

    private XmlConstructorBean1 xmlConstructorBean1;

    public XmlConstructorBean1 getXmlConstructorBean1() {
        return xmlConstructorBean1;
    }

}
