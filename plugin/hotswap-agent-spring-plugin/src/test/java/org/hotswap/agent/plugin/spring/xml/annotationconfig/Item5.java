package org.hotswap.agent.plugin.spring.xml.annotationconfig;

public class Item5 {

    public Item5(String name, String name2) {
        this.name = name;
        this.name2 = name2;
    }

    private String name;
    private String name2;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName2() {
        return name2;
    }

    public void setName2(String name2) {
        this.name2 = name2;
    }
}
