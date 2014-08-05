package org.hotswap.agent.annotation;

/**
 * Type of the resource change event on filesystem.
 */
public enum FileEvent {

    /**
     * New file or directory is created.
     */
    CREATE,

    /**
     * Existing file or directory is modified.
     */
    MODIFY,

    /**
     * Existing file is deleted.
     */
    DELETE
}
