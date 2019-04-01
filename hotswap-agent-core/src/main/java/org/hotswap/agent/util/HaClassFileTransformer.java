package org.hotswap.agent.util;

import java.lang.instrument.ClassFileTransformer;

/**
 * The Interface HaClassFileTransformer.
 */
public interface HaClassFileTransformer extends ClassFileTransformer {

    /**
     * True if this transformer handle only redefinitions
     *
     * @return true, if is for redefinition only
     */
    public boolean isForRedefinitionOnly();

}
