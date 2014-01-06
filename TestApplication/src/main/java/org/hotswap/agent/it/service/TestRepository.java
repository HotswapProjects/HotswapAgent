package org.hotswap.agent.it.service;

import org.springframework.stereotype.Repository;

/**
 * Created by bubnik on 3.1.14.
 */
@Repository
public class TestRepository {
    public String helloWorld() {
        return "Hello world";
    }
}
