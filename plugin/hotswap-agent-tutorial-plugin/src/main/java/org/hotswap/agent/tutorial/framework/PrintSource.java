package org.hotswap.agent.tutorial.framework;

/**
 * Content provider for printing service.
 */
public interface PrintSource {

    /**
     * Get the content to print. Please note that for performance reasons the resolved content
     * may be cached for repeated printing.
     *
     * @return the conent
     */
    String getPrintContent();
}
