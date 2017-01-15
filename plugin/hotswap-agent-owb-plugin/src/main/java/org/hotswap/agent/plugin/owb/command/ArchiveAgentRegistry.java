package org.hotswap.agent.plugin.owb.command;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.xbean.finder.archive.Archive;

/**
 * ArchiveAgentRegistry - maps archivePath to BeanArchiveAgent. This class is separated from BeanArchiveAgent
 * to avoid "class not found exception" when map is accessed from ClassPathBeanRefreshCommand.
 *
 * @author Vladimir Dvorak
 */
public class ArchiveAgentRegistry {

    // map archive path -> BeanArchiveAgent
    private static Map<String, BeanArchiveAgent> INSTANCES = new ConcurrentHashMap<String, BeanArchiveAgent>();

    public static Map<String, BeanArchiveAgent> getInstances() {
        return INSTANCES;
    }

    public static boolean contains(String archivePath) {
        return INSTANCES.containsKey(archivePath);
    }

    public static void put(String archivePath, BeanArchiveAgent bdaAgent) {
        INSTANCES.put(archivePath, bdaAgent);
    }

    public static BeanArchiveAgent get(String archivePath) {
        return INSTANCES.get(archivePath);
    }

    public static Collection<BeanArchiveAgent> values() {
        return INSTANCES.values();
    }

    /**
     * Iterate over agents and find the one containing the class by name
     *
     * @param className
     * @return
     */
    public static String getArchiveByClassName(String className){
        for(BeanArchiveAgent agent: INSTANCES.values()) {
            Iterator<Archive.Entry> iter = agent.getBeanArchive().iterator();
            while (iter.hasNext()) {
                String archiveClassName = iter.next().getName();
                if (className.equals(archiveClassName)) {
                    return agent.getArchivePath();
                }
            }
        }
        return null;
    }
}
