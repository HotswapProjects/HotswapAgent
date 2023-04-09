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
package org.hotswap.agent.plugin.hibernate;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.Manifest;
import org.hotswap.agent.annotation.Maven;
import org.hotswap.agent.annotation.Name;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Versions;
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
 */
@Plugin(name = "Hibernate",
        group = "groupHibernate",
        fallback = true,
        description = "Reload Hibernate configuration after entity create/change.",
        testedVersions = {"All between 4.0.1 - 4.3.11, 5.0.0 - 5.2.10"},
        expectedVersions = {"4.0.x", "4.1.x", "4.2.x", "5.0.[0-4,7-x]", "5.1.x", "5.2.x", "5.3.x", "5.4.x" },
        supportClass = {HibernateTransformers.class})
@Versions(
        maven = {
            @Maven(value = "[4.0,6.0)", artifactId = "hibernate-core", groupId = "org.hibernate"),
            @Maven(value = "[4.0,6.0)", artifactId = "hibernate-entitymanager", groupId = "org.hibernate"),
        },
        manifest= {
                @Manifest(value="[4.0,6.0)", names= {
                        @Name(key=Name.BundleSymbolicName, value="org.hibernate.validator")
                }),
                @Manifest(value="[4.0,6.0)", names= {
                        @Name(key=Name.BundleSymbolicName, value="org.hibernate.entitymanager")
                }),
                @Manifest(value="[4.0,6.0)", names= {
                        @Name(key=Name.BundleSymbolicName, value="org.hibernate.core")
                }),
                @Manifest(value="[4.0,6.0)", names= {
                        @Name(key=Name.ImplementationUrl, value="http://hibernate.org"),
                        @Name(key=Name.ImplementationVendorId, value="org.hibernate")
                }),
        }
        )
public class HibernatePlugin {
    private static final String ENTITY_ANNOTATION = "javax.persistence.Entity";
    private static AgentLogger LOGGER = AgentLogger.getLogger(HibernatePlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> regAnnotatedMetaDataProviders = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    Map<Object, String> regBeanMetaDataManagersMap = new WeakHashMap<Object, String>();

    // refresh commands
    Command reloadEntityManagerFactoryCommand =
            new ReflectionCommand(this, HibernateRefreshCommands.class.getName(), "reloadEntityManagerFactory");
    Command reloadSessionFactoryCommand =
            new ReflectionCommand(this, HibernateRefreshCommands.class.getName(), "reloadSessionFactory");

    private Command invalidateHibernateValidatorCaches = new Command() {
        @Override
        public void executeCommand() {
            LOGGER.debug("Refreshing BeanMetaDataManagerCache/AnnotatedMetaDataProvider cache.");

            try {
                Method resetCacheMethod1 = resolveClass("org.hibernate.validator.internal.metadata.provider.AnnotationMetaDataProvider").getDeclaredMethod("$$ha$resetCache");
                for (Object regAnnotatedDataManager : regAnnotatedMetaDataProviders) {
                    LOGGER.debug("Invoking org.hibernate.validator.internal.metadata.provider.AnnotationMetaDataProvider.$$ha$resetCache on {}", regAnnotatedDataManager);
                    resetCacheMethod1.invoke(regAnnotatedDataManager);
                }
                for (Map.Entry<Object, String> entry : regBeanMetaDataManagersMap.entrySet()) {
                    LOGGER.debug("Invoking " + entry.getValue() + " .$$ha$resetCache on {}", entry.getKey());
                    Method resetCacheMethod2 = resolveClass(entry.getValue()).getDeclaredMethod("$$ha$resetCache");
                    resetCacheMethod2.invoke(entry.getKey());
                }
            } catch (Exception e) {
                LOGGER.error("Error refreshing BeanMetaDataManagerCache/AnnotatedMetaDataProvider cache.", e);
            }
        }
    };

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
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
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
     * and delete/create event sequence - than create is cached by this event and hotswap for
     * the same class by entityReload.
     */
    @OnClassFileEvent(classNameRegexp = ".*", events = {FileEvent.CREATE})
    public void newEntity(CtClass clazz) throws Exception {
        if (AnnotationHelper.hasAnnotation(clazz, ENTITY_ANNOTATION)) {
            refresh(500);
        }
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache() throws Exception {
        if (!regBeanMetaDataManagersMap.isEmpty() || !regAnnotatedMetaDataProviders.isEmpty()) {
            scheduler.scheduleCommand(invalidateHibernateValidatorCaches);
        }
    }

    // reload the configuration - schedule a command to run in the application classloader and merge
    // duplicate commands.
    private void refresh(int timeout) {
        if (hibernateEjb) {
            scheduler.scheduleCommand(reloadEntityManagerFactoryCommand, timeout);
        } else {
            scheduler.scheduleCommand(reloadSessionFactoryCommand, timeout);
        }
    }

    public void registerAnnotationMetaDataProvider(Object annotatedMetaDataProvider) {
        regAnnotatedMetaDataProviders.add(annotatedMetaDataProvider);
    }


    public void registerBeanMetaDataManager(Object beanMetaDataManager, String beanMetaDataManagerClassName) {
        regBeanMetaDataManagersMap.put(beanMetaDataManager, beanMetaDataManagerClassName);
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}

