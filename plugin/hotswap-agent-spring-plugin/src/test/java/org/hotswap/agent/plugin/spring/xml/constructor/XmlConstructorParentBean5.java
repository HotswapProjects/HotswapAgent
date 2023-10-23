package org.hotswap.agent.plugin.spring.xml.constructor;

import org.springframework.beans.factory.annotation.Autowired;

public class XmlConstructorParentBean5 {

    private XmlConstructorBean5 xmlConstructorBean5;

    @Autowired
    public XmlConstructorParentBean5(XmlConstructorBean5 xmlConstructorBean5) {
        this.xmlConstructorBean5 = xmlConstructorBean5;
    }

    public XmlConstructorBean5 getXmlConstructorBean5() {
        return xmlConstructorBean5;
    }


    public void setXmlConstructorBean5(XmlConstructorBean5 xmlConstructorBean5) {
        this.xmlConstructorBean5 = xmlConstructorBean5;
    }
}
