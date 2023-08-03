package org.hotswap.agent.plugin.spring.factorybean.xml;

public class XmlFactBean2 {

    public XmlFactBean2(String value) {
        this.name = value;
    }
    private String name = "XmlFactBean2";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
