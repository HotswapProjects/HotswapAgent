/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.hibernate3.jpa;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.Maven;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Versions;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.hibernate3.jpa.Hibernate3JPARefreshCommands;
import org.hotswap.agent.util.AnnotationHelper;

/**
 * Reload Hibernate configuration after entity create/change.
 *
 * <b>This plugin requires  Hibernate3 plugin to be enabled also to work!!</b>
 *
 * @author Jiri Bubnik
 * @author alpapad@gmail.com
 */

@Plugin(name = "Hibernate3JPA", //
        group = "groupHibernate",
        description = "Reload Hibernate configuration after entity create/change.", //
        testedVersions = { "3.6" }, //
        expectedVersions = { "3.6" }, //
        supportClass = { Hibernate3JPATransformers.class })
@Versions(maven = { @Maven(value = "[3.0,4.0)", artifactId = "hibernate-entitymanager", groupId = "org.hibernate") })
public class Hibernate3JPAPlugin {

    /** The Constant ENTITY_ANNOTATION. */
    private static final String ENTITY_ANNOTATION = "javax.persistence.Entity";

    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(Hibernate3JPAPlugin.class);

    /** The scheduler. */
    @Init
    Scheduler scheduler;

    /** The app class loader. */
    @Init
    ClassLoader appClassLoader;

    /** The reg annotated meta data providers. */
    Set<Object> regAnnotatedMetaDataProviders = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    /** The reg bean meta data managers. */
    Set<Object> regBeanMetaDataManagers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    /** The reload entity manager factory command. */
    // refresh commands
    Command reloadEntityManagerFactoryCommand = new ReflectionCommand(this, Hibernate3JPARefreshCommands.class.getName(),
            "reloadEntityManagerFactory");


    /** The invalidate hibernate validator caches. */
    private Command invalidateHibernateValidatorCaches = new Command() {
        @Override
        public void executeCommand() {
            LOGGER.debug("Refreshing BeanMetaDataManagerCache/AnnotatedMetaDataProvider cache.");

            try {
                Method resetCacheMethod1 = resolveClass(
                        "org.hibernate.validator.internal.metadata.provider.AnnotationMetaDataProvider")
                                .getDeclaredMethod("__resetCache");
                for (Object regAnnotatedDataManager : regAnnotatedMetaDataProviders) {
                    LOGGER.debug(
                            "Invoking org.hibernate.validator.internal.metadata.provider.AnnotationMetaDataProvider.__resetCache on {}",
                            regAnnotatedDataManager);
                    resetCacheMethod1.invoke(regAnnotatedDataManager);
                }
                Method resetCacheMethod2 = resolveClass("org.hibernate.validator.internal.metadata.BeanMetaDataManager")
                        .getDeclaredMethod("__resetCache");
                for (Object regBeanMetaDataManager : regBeanMetaDataManagers) {
                    LOGGER.debug(
                            "Invoking org.hibernate.validator.internal.metadata.BeanMetaDataManager.__resetCache on {}",
                            regBeanMetaDataManager);
                    resetCacheMethod2.invoke(regBeanMetaDataManager);
                }
            } catch (Exception e) {
                LOGGER.error("Error refreshing BeanMetaDataManagerCache/AnnotatedMetaDataProvider cache.", e);
            }
        }
    };

    /** The hibernate ejb. */
    // is EJB3 or plain hibernate
    boolean hibernateEjb;

    /**
     * Plugin initialization properties (from Hibernate3JPAHelper or
     * SessionFactoryProxy).
     *
     * @param version the version
     * @param hibernateEjb the hibernate ejb
     */
    public void init(String version, Boolean hibernateEjb) {
        LOGGER.info("Hibernate3 JPA plugin initialized - Hibernate Core version '{}'", version);
        this.hibernateEjb = hibernateEjb;
    }

    /**
     * Reload after entity class change. It covers also @Entity annotation
     * removal.
     *
     * @param clazz the clazz
     * @param original the original
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void entityReload(CtClass clazz, Class<?> original) {
        // TODO list of entity/resource files is known to hibernate, better to
        // check this list
        if (AnnotationHelper.hasAnnotation(original, ENTITY_ANNOTATION)
                || AnnotationHelper.hasAnnotation(clazz, ENTITY_ANNOTATION)) {
            LOGGER.debug("Entity reload class {}, original classloader {}", clazz.getName(), original.getClassLoader());
            refresh(100);
        }
    }

    /**
     * New entity class - not covered by reloading mechanism.
     * <p/>
     * Increase the reload timeout to avoid duplicate reloading in case of
     * recompile with IDE and delete/create event sequence - than create is
     * cached by this event and hotswap for the same class by entityReload.
     *
     * @param clazz the clazz
     * @throws Exception the exception
     */
    @OnClassFileEvent(classNameRegexp = ".*", events = { FileEvent.CREATE })
    public void newEntity(CtClass clazz) throws Exception {
        if (AnnotationHelper.hasAnnotation(clazz, ENTITY_ANNOTATION)) {
            refresh(500);
        }
    }

    /**
     * Reload on hbm file modifications.
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


    /**
     * Reload on persistence.xml file modifications
     */
    @OnResourceFileEvent(path = "/", filter = "persistence.xml")
    public void refreshOnPersistenceXml(){
        refresh(500);
    }


    /**
     * Invalidate class cache.
     *
     * @throws Exception the exception
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache() throws Exception {
        if (!regBeanMetaDataManagers.isEmpty() || !regAnnotatedMetaDataProviders.isEmpty()) {
            scheduler.scheduleCommand(invalidateHibernateValidatorCaches);
        }
    }

    // reload the configuration - schedule a command to run in the application
    // classloader and merge
    /**
     * Refresh.
     *
     * @param timeout the timeout
     */
    // duplicate commands.
    private void refresh(int timeout) {
        scheduler.scheduleCommand(reloadEntityManagerFactoryCommand, timeout);
    }

    /**
     * Register annotation meta data provider.
     *
     * @param annotatedMetaDataProvider the annotated meta data provider
     */
    public void registerAnnotationMetaDataProvider(Object annotatedMetaDataProvider) {
        regAnnotatedMetaDataProviders.add(annotatedMetaDataProvider);
    }

    /**
     * Register bean meta data manager.
     *
     * @param beanMetaDataManager the bean meta data manager
     */
    public void registerBeanMetaDataManager(Object beanMetaDataManager) {
        regBeanMetaDataManagers.add(beanMetaDataManager);
    }

    /**
     * Resolve class.
     *
     * @param name the name
     * @return the class
     * @throws ClassNotFoundException the class not found exception
     */
    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
