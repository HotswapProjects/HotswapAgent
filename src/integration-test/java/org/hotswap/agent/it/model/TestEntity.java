package org.hotswap.agent.it.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Test entity
 */
@Entity
public class TestEntity {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    //@Transient
    private String value;

    public TestEntity() {
// Uncomment and reorder to test AnonymousClassPatchPlugin
//        new Exception() {};
//        new Serializable() {};
//        new Cloneable() {};
    }

    public TestEntity(String value) {
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "TestEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
