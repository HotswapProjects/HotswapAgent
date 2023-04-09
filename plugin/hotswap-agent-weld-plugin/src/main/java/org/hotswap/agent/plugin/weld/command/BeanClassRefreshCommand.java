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
package org.hotswap.agent.plugin.weld.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.weld.BeanReloadStrategy;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * BeanClassRefreshCommand. Collect all classes definitions/redefinitions for single archive
 *
 * 1. Merge all commands (definition, redefinition) for single archive to single command.
 * 2. Call proxy redefinitions in BeanClassRefreshAgent for all merged commands
 * 3. Call bean class reload in BeanDepoymentArchiveAgent for all merged commands
 *
 * @author Vladimir Dvorak
 */
public class BeanClassRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(BeanClassRefreshCommand.class);

    ClassLoader classLoader;

    String archivePath;

    String className;

    private String oldFullSignature;

    String oldSignatureForProxyCheck;

    String oldSignatureByStrategy;

    String strBeanReloadStrategy;

    Class<?> beanClass;

    Map<Object, Object> registeredProxiedBeans;

    // either event or classDefinition is set by constructor (watcher or transformer)
    WatchFileEvent event;

    public BeanClassRefreshCommand(ClassLoader classLoader, String archivePath, Map<Object, Object> registeredProxiedBeans,
            String className, String oldFullSignature, String oldSignatureForProxyCheck, String oldSignatureByStrategy, BeanReloadStrategy beanReloadStrategy) {
        this.classLoader = classLoader;
        this.archivePath = archivePath;
        this.registeredProxiedBeans = registeredProxiedBeans;
        this.className = className;
        this.oldFullSignature = oldFullSignature;
        this.oldSignatureForProxyCheck = oldSignatureForProxyCheck;
        this.oldSignatureByStrategy = oldSignatureByStrategy;
        this.strBeanReloadStrategy = beanReloadStrategy != null ? beanReloadStrategy.toString() : null;
    }

    public BeanClassRefreshCommand(ClassLoader classLoader, String normalizedArchivePath, WatchFileEvent event) {
        this.classLoader = classLoader;
        this.archivePath = normalizedArchivePath;
        this.event = event;

        // strip from URI prefix up to basePackage and .class suffix.
        String classFullPath = event.getURI().getPath();
        int index = classFullPath.indexOf(normalizedArchivePath);
        if (index == 0) {
            // Strip archive path from beginning and .class from the end to get class name from full path to class file
            String classPath = classFullPath.substring(normalizedArchivePath.length());
            classPath = classPath.substring(0, classPath.indexOf(".class"));
            if (classPath.startsWith("/")) {
                classPath = classPath.substring(1);
            }
            this.className = classPath.replace("/", ".");
        } else {
            LOGGER.error("Archive path '{}' doesn't match with classFullPath '{}'", normalizedArchivePath, classFullPath);
        }
    }

    @Override
    public void executeCommand() {
        List<Command> mergedCommands = popMergedCommands();
        mergedCommands.add(0, this);

        do {
            for (Command cmd: mergedCommands) {
                BeanClassRefreshCommand bcrCmd = (BeanClassRefreshCommand) cmd;
                try {
                    bcrCmd.beanClass = classLoader.loadClass(bcrCmd.className);
                } catch (ClassNotFoundException e) {
                    LOGGER.error("Class '{}' not found in classLoader {}", bcrCmd.className, classLoader);
                }
            }

            for (Command cmd: mergedCommands) {
                ((BeanClassRefreshCommand)cmd).recreateProxy(mergedCommands);
            }

            Map<String, String> oldFullSignatures = new HashMap<>();
            Map<String, String> oldSignatures = new HashMap<>();

            for (Command cmd: mergedCommands) {
                BeanClassRefreshCommand bcrCmd = (BeanClassRefreshCommand) cmd;
                oldFullSignatures.put(bcrCmd.className, bcrCmd.oldFullSignature);
                oldSignatures.put(bcrCmd.className, bcrCmd.oldSignatureByStrategy);
            }

            for (Command cmd1: mergedCommands) {
                BeanClassRefreshCommand bcrCmd1 = (BeanClassRefreshCommand) cmd1;
                boolean found = false;
                for (Command cmd2: mergedCommands) {
                    BeanClassRefreshCommand bcrCmd2 = (BeanClassRefreshCommand) cmd2;
                    if (bcrCmd1 != bcrCmd2 && !bcrCmd1.beanClass.equals(bcrCmd2.beanClass) && bcrCmd2.beanClass.isAssignableFrom(bcrCmd1.beanClass)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    bcrCmd1.reloadBean(mergedCommands, oldFullSignatures, oldSignatures);
                }
            }
            mergedCommands = popMergedCommands();
        } while (!mergedCommands.isEmpty());
    }

    private void recreateProxy(List<Command> mergedCommands) {

        if (isDeleteEvent(mergedCommands)) {
            LOGGER.trace("Skip WELD recreate proxy for delete event on class '{}'", className);
            return;
        }

        if (className != null) {
            try {
                LOGGER.debug("Executing BeanClassRefreshAgent.recreateProxy('{}')", className);
                Class<?> bdaAgentClazz = Class.forName(BeanClassRefreshAgent.class.getName(), true, classLoader);
                Method recreateProxy  = bdaAgentClazz.getDeclaredMethod("recreateProxy",
                        new Class[] { ClassLoader.class,
                                      String.class,
                                      Map.class,
                                      String.class,
                                      String.class
                        }
                );
                recreateProxy.invoke(null,
                        classLoader,
                        archivePath,
                        registeredProxiedBeans,
                        className,
                        oldSignatureForProxyCheck
                );
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Plugin error, method not found", e);
            } catch (InvocationTargetException e) {
                LOGGER.error("Error recreateProxy class {} in classLoader {}", e, className, classLoader);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Plugin error, illegal access", e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Plugin error, CDI class not found in classloader", e);
            }
        }
    }

    private void reloadBean(List<Command> mergedCommands, Map<String, String> oldFullSignatures, Map<String, String> oldSignatures) {

        if (isDeleteEvent(mergedCommands)) {
            LOGGER.trace("Skip WELD refresh bean class for delete event on class '{}'", className);
            return;
        }

        if (className != null) {
            try {
                LOGGER.debug("Executing BeanClassRefreshAgent.reloadBean('{}')", className);
                Class<?> bdaAgentClazz = Class.forName(BeanClassRefreshAgent.class.getName(), true, classLoader);
                Method refreshBean  = bdaAgentClazz.getDeclaredMethod("reloadBean",
                        new Class[] { ClassLoader.class,
                                      String.class,
                                      String.class,
                                      Map.class,
                                      Map.class,
                                      String.class
                        }
                );
                refreshBean.invoke(null,
                        classLoader,
                        archivePath,
                        className,
                        oldFullSignatures,
                        oldSignatures,
                        strBeanReloadStrategy // passed as String since BeanClassRefreshAgent has different classloader
                );
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Plugin error, method not found", e);
            } catch (InvocationTargetException e) {
                LOGGER.error("Error reloadBean class '{}' in classLoader '{}'", e, className, classLoader);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Plugin error, illegal access", e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Plugin error, CDI class not found in classloader", e);
            }
        }
    }

    /**
     * Check all merged events with same className for delete and create events. If delete without create is found, than assume
     * file was deleted.
     * @param mergedCommands
     */
    private boolean isDeleteEvent(List<Command> mergedCommands) {
        boolean createFound = false;
        boolean deleteFound = false;
        for (Command cmd : mergedCommands) {
            BeanClassRefreshCommand refreshCommand = (BeanClassRefreshCommand) cmd;
            if (className.equals(refreshCommand.className)) {
                if (refreshCommand.event != null) {
                    if (refreshCommand.event.getEventType().equals(FileEvent.DELETE))
                        deleteFound = true;
                    if (refreshCommand.event.getEventType().equals(FileEvent.CREATE))
                        createFound = true;
                }
            }
        }

        LOGGER.trace("isDeleteEvent result {}: createFound={}, deleteFound={}", createFound, deleteFound);
        return !createFound && deleteFound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BeanClassRefreshCommand that = (BeanClassRefreshCommand) o;

        if (!classLoader.equals(that.classLoader)) return false;
        if (archivePath != null && !archivePath.equals(that.archivePath)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = classLoader.hashCode();
        result = 31 * result + (archivePath != null ? archivePath.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BeanClassRefreshCommand{" +
                "classLoader=" + classLoader +
                ", archivePath='" + archivePath + '\'' +
                ", className='" + className + '\'' +
                '}';
    }
}
