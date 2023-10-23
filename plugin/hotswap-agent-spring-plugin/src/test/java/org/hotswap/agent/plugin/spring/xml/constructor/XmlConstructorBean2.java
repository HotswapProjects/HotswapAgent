package org.hotswap.agent.plugin.spring.xml.constructor;

public class XmlConstructorBean2 {

    public XmlConstructorBean2(String value) {
        this.name = value;
    }
    private String name = "xmlConstructorBean2";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
