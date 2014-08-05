package org.hotswap.agent.annotation;

/**
 * Type of event, when a class is loaded by a ClassLoader.
 */
public enum LoadEvent {
    /**
     * Should the event be raised when class is first loaded by the ClassLoader?
     */
    DEFINE,

    /**
     * Should the event be raised when class is redefined by hotswap mechanism?
     */
    REDEFINE;
}
