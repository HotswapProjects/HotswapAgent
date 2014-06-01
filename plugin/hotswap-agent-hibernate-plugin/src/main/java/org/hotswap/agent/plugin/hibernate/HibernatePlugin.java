package org.hotswap.agent.plugin.hibernate;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.annotation.Watch;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.watch.WatchEvent;

/**
 * Reload Hibernate configuration after entity create/change.
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Hibernate", description = "Reload Hibernate configuration after entity create/change.",
        testedVersions = {"All between 4.0.1 - 4.2.13"},
        expectedVersions = {"4.0.x", "4.1.x", "4.2.x"},
        supportClass = {HibernateTransformers.class})
public class HibernatePlugin {
    private static final String ENTITY_ANNOTATION = "javax.persistence.Entity";
    private static AgentLogger LOGGER = AgentLogger.getLogger(HibernatePlugin.class);

    @Init
    Scheduler scheduler;

    // refresh commands
    Command reloadEntityManagerFactoryCommand =
            new ReflectionCommand(this, HibernateRefreshCommands.class.getName(), "reloadEntityManagerFactory");
    Command reloadSessionFactoryCommand =
            new ReflectionCommand(this, HibernateRefreshCommands.class.getName(), "reloadSessionFactory");

    // is EJB3 or plain hibernate
    boolean hibernateEjb;

    /**
     * Plugin initialization properties (from HibernatePersistenceHelper or SessionFactoryProxy)
     */
    public void init(String version, Boolean hibernateEjb) {
        LOGGER.info("Hibernate plugin initialized - Hibernate Core version '{}'", version);
        this.hibernateEjb = hibernateEjb;
    }

    /**
     * Reload after entity class change. It covers also @Entity annotation removal.
     */
    @Transform(classNameRegexp = ".*", onDefine = false)
    public void entityReload(CtClass clazz, Class original) {
        // TODO list of entity/resource files is known to hibernate, better to check this list
        if (AnnotationHelper.hasAnnotation(original, ENTITY_ANNOTATION)
                || AnnotationHelper.hasAnnotation(clazz, ENTITY_ANNOTATION)
                ) {
            LOGGER.debug("Entity reload class {}, original classloader {}", clazz.getName(), original.getClassLoader());
            refresh(100);
        }
    }

    /**
     * New entity class - not covered by reloading mechanism.
     * <p/>
     * Increase the reload timeout to avoid duplicate reloading in case of recompile with IDE
     * and delete/create event sequence - than create is cached by this even and hotswap for
     * the same class by entityReload.
     */
    @Watch(path = ".", filter = ".*.class", watchEvents = {WatchEvent.WatchEventType.CREATE})
    public void newEntity(CtClass clazz) throws Exception {
        if (AnnotationHelper.hasAnnotation(clazz, ENTITY_ANNOTATION)) {
            refresh(500);
        }
    }

    // reload the configuration - schedule a command to run in the application classloader and merge
    // duplicate commands.
    private void refresh(int timeout) {
        if (hibernateEjb)
            scheduler.scheduleCommand(reloadEntityManagerFactoryCommand, timeout);
        else
            scheduler.scheduleCommand(reloadSessionFactoryCommand, timeout);
    }


}

