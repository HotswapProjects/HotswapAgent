package org.hotswap.agent.plugin.hibernate3.testEntities;

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

    private String description;

    public TestEntity() {
    }

    public TestEntity(String name, String description) {
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
