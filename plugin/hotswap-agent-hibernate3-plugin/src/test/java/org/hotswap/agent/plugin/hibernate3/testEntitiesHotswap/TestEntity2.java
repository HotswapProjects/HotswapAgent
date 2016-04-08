package org.hotswap.agent.plugin.hibernate3.testEntitiesHotswap;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Transient;

/**
 * Test entity
 */
@Entity
public class TestEntity2 {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    // Make description transient to check that reload was successful
    @Transient
    private String description;

    public TestEntity2() {
    }

    public TestEntity2(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
