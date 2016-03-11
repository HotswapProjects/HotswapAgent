package org.hotswap.agent.plugin.weld.command;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BdaAgentRegistry - maps archivePath to BeanDeploymentArchiveAgent. This class is separated from BeanDeploymentArchiveAgent
 * to avoid "class not found exception" when map is accessed from ClassPathBeanRefreshCommand.
 *
 * @author Vladimir Dvorak
 */
public class BdaAgentRegistry {

    // map archive path -> BeanDeploymentArchiveAgent
    private static Map<String, BeanDeploymentArchiveAgent> INSTANCES = new ConcurrentHashMap<String, BeanDeploymentArchiveAgent>();

    public static Map<String, BeanDeploymentArchiveAgent> getInstances() {
        return INSTANCES;
    }

    public static boolean contains(String archivePath) {
        return INSTANCES.containsKey(archivePath);
    }

    public static void put(String archivePath, BeanDeploymentArchiveAgent bdaAgent) {
        INSTANCES.put(archivePath, bdaAgent);
    }

    public static BeanDeploymentArchiveAgent get(String archivePath) {
        return INSTANCES.get(archivePath);
    }

    public static Collection<BeanDeploymentArchiveAgent> values() {
        return INSTANCES.values();
    }

}
