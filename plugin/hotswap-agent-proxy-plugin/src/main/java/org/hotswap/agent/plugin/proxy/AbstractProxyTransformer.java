package org.hotswap.agent.plugin.proxy;

import org.hotswap.agent.javassist.ClassPool;

/**
 * Redefines a proxy
 *
 * @author Erki Ehtla
 *
 */
public abstract class AbstractProxyTransformer implements ProxyTransformer {
    public AbstractProxyTransformer(Class<?> classBeingRedefined, ClassPool classPool) {
        super();
        this.classBeingRedefined = classBeingRedefined;
        this.classPool = classPool;
    }

    protected ProxyBytecodeGenerator generator;
    protected ProxyBytecodeTransformer transformer;
    protected Class<?> classBeingRedefined;
    protected ClassPool classPool;

    protected ProxyBytecodeGenerator getGenerator() {
        if (generator == null) {
            generator = createGenerator();
        }
        return generator;
    }

    protected ProxyBytecodeTransformer getTransformer() {
        if (transformer == null) {
            transformer = createTransformer();
        }
        return transformer;
    }

    /**
     * creates a new ProxyBytecodeGenerator insatance for use in this transformer
     *
     * @return
     */
    protected abstract ProxyBytecodeGenerator createGenerator();

    /**
     * creates a new ProxyBytecodeTransformer insatance for use in this transformer
     *
     * @return
     */
    protected abstract ProxyBytecodeTransformer createTransformer();

    /**
     * Checks if there were changes that require the redefinition of the proxy
     *
     * @return true if there wre changes that require redefinition
     */
    protected boolean isTransformingNeeded() {
        return ProxyClassSignatureHelper.isNonSyntheticPoolClassOrParentDifferent(classBeingRedefined, classPool);
    }

}