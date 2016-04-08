package org.hotswap.agent.plugin.hibernate3.session;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AnnotationHelper;

/**
 * Reload Hibernate configuration after entity create/change.
 *
 * @author Jiri Bubnik
 * @author alpapad@gmail.com
 */
@Plugin(name = "Hibernate3", //
		description = "Reload Hibernate configuration after entity create/change.", //
		testedVersions = { "3.6" }, //
		expectedVersions = { "3.6" }, //
		supportClass = { Hibernate3Transformers.class })
public class Hibernate3Plugin {
	private static final String ENTITY_ANNOTATION = "javax.persistence.Entity";
	private static AgentLogger LOGGER = AgentLogger.getLogger(Hibernate3Plugin.class);

	@Init
	Scheduler scheduler;

	@Init
	ClassLoader appClassLoader;


	String version;
	
	// refresh command
	private final Command reloadSessionFactoryCommand = new ReflectionCommand(this, Hibernate3RefreshCommand.class.getName(), "reloadSessionFactory");

	/**
	 * Plugin initialization properties (from Hibernate3JPAHelper or
	 * SessionFactoryProxy)
	 */
	@Init
	public void init() {
		LOGGER.info("Hibernate3 Session plugin initialized", version);
	}

	boolean enabled = true;

	/**
	 * Disable plugin (if environment is JPA)
	 * 
	 * Need to re-think this: Maybe use OverrideConfig to hold this info?
	 */
	public void disable() {
		LOGGER.info("Disabling Hibernate3 Session plugin since JPA is active");
		this.enabled = false;
	}
	
	public void setVersion(String v){
		this.version = v;
		LOGGER.info("Hibernate Core version '{}'", version);
	}
	
	/**
	 * Reload after entity class change. It covers also @Entity annotation
	 * removal.
	 */
	@OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
	public void entityReload(CtClass clazz, Class<?> original) {
		// TODO list of entity/resource files is known to hibernate, 
		// better to check this list
		if (AnnotationHelper.hasAnnotation(original, ENTITY_ANNOTATION) || AnnotationHelper.hasAnnotation(clazz, ENTITY_ANNOTATION)) {
			LOGGER.debug("Entity reload class {}, original classloader {}", clazz.getName(), original.getClassLoader());
			refresh(500);
		}
	}

	/**
	 * New entity class - not covered by reloading mechanism.
	 * <p/>
	 * Increase the reload timeout to avoid duplicate reloading in case of
	 * recompile with IDE and delete/create event sequence - than create is
	 * cached by this event and hotswap for the same class by entityReload.
	 */
	@OnClassFileEvent(classNameRegexp = ".*", events = { FileEvent.CREATE })
	public void newEntity(CtClass clazz) throws Exception {
		if (AnnotationHelper.hasAnnotation(clazz, ENTITY_ANNOTATION)) {
			refresh(500);
		}
	}
	
	/**
	 * Reload on hbm file modifications
	 */
	@OnResourceFileEvent(path = "/", filter = ".*.hbm.xml")
	public void refreshOnHbm(){
		refresh(500);
	}
	
	/**
	 * Reload on hibernate.cfg.xml file modifications
	 */
	@OnResourceFileEvent(path = "/", filter = ".*.cfg.xml")
	public void refreshOnCfg(){
		refresh(500);
	}
	
	// reload the configuration - schedule a command to run in the application
	// classloader and merge duplicate commands.
	public void refresh(int timeout) {
		if(enabled) {
			scheduler.scheduleCommand(reloadSessionFactoryCommand, timeout);
		}
	}

}
