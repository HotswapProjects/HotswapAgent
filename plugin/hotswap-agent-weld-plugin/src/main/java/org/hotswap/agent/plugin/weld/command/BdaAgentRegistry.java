package org.hotswap.agent.plugin.weld.command;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BdaAgentRegistry - maps bdaId to BeanDeploymentArchiveAgent. This class is separated from BeanDeploymentArchiveAgent
 * to avoid "class not found exception" when map is accessed from ClassPathBeanRefreshCommand.
 */
public class BdaAgentRegistry {

    // map bdaId-> BeanDeploymentArchiveAgent
    private static Map<String, BeanDeploymentArchiveAgent> INSTANCES = new ConcurrentHashMap<String, BeanDeploymentArchiveAgent>();

    public static Map<String, BeanDeploymentArchiveAgent> getInstances() {
        return INSTANCES;
    }

    public static Boolean contains(String bdaId) {
        return INSTANCES.containsKey(bdaId);
    }

    public static void put(String bdaId, BeanDeploymentArchiveAgent bdaAgent) {
        INSTANCES.put(bdaId, bdaAgent);
    }

    public static BeanDeploymentArchiveAgent get(String bdaId) {
        return INSTANCES.get(bdaId);
    }

    public static Collection<BeanDeploymentArchiveAgent> values() {
        return INSTANCES.values();
    }

    public static BeanDeploymentArchiveAgent getByBdaIdFromValues(String bdaId) {
        if (bdaId != null) {
            for (BeanDeploymentArchiveAgent bdaAgent : INSTANCES.values()) {
                if (bdaId.equals(bdaAgent.getBdaId())) {
                    return bdaAgent;
                }
            }
        }
        return null;
    }

}
