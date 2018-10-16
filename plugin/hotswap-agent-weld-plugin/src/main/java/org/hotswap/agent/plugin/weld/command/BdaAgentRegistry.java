package org.hotswap.agent.plugin.weld.command;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BdaAgentRegistry - maps archivePath to BeanClassRefreshAgent. This class is separated from BeanClassRefreshAgent
 * to avoid "class not found exception" when map is accessed from ClassPathBeanRefreshCommand.
 *
 * @author Vladimir Dvorak
 */
public class BdaAgentRegistry {

    // map archive path -> BeanClassRefreshAgent
    private static Map<String, BeanClassRefreshAgent> INSTANCES = new ConcurrentHashMap<String, BeanClassRefreshAgent>();

    public static Map<String, BeanClassRefreshAgent> getInstances() {
        return INSTANCES;
    }

    public static boolean contains(String archivePath) {
        return INSTANCES.containsKey(archivePath);
    }

    public static void put(String archivePath, BeanClassRefreshAgent bdaAgent) {
        INSTANCES.put(archivePath, bdaAgent);
    }

    public static BeanClassRefreshAgent get(String archivePath) {
        return INSTANCES.get(archivePath);
    }

    public static Collection<BeanClassRefreshAgent> values() {
        return INSTANCES.values();
    }

    /**
     * Iterate over agents and find the one containing the class by name
     *
     * @param className
     * @return
     */
    public static String getArchiveByClassName(String className){
        for(BeanClassRefreshAgent agent: INSTANCES.values()) {
            if(agent.getDeploymentArchive().getBeanClasses().contains(className)) {
                return agent.getArchivePath();
            }
        }
        return null;
    }
}
