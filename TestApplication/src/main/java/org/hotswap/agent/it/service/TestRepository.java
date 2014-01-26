package org.hotswap.agent.it.service;

import org.springframework.stereotype.Repository;

/**
 * Hello world repository.
 */
@Repository
public class TestRepository {
    public String helloWorld() {
        return "Hello world";
    }
}
