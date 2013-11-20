package org.hotswap.agent.testData;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Test entity
 */
@Entity
public class SimpleEntity {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
}
