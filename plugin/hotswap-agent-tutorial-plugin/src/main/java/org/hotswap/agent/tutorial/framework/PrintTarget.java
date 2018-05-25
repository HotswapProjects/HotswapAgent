package org.hotswap.agent.tutorial.framework;

/**
 * Simple Printing target - default print to output, but implement your printing target for example to support
 * unit tests.
 */
public interface PrintTarget {

    /**
     * Content to print.
     * @param content content to print
     */
    default void print(String content) {
        System.out.println(content);
    }
}
