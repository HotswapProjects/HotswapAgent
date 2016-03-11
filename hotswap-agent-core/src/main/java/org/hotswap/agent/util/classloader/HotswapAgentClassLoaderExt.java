package org.hotswap.agent.util.classloader;

/**
 * Interface used to extending class loaders by extra path defined in hotspwap-agent.properties
 */
public interface HotswapAgentClassLoaderExt {
    public void setExtraClassPath(java.net.URL[] extraClassPath);
}
