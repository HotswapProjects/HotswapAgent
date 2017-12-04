package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.watch.WatchFileEvent;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Do refresh Spring class (scanned by xml) based on xml files.
 *
 * This commands merges events of watcher.event(CREATE) and transformer hotswap reload to a single refresh command.
 */
public class XmlBeanRefreshCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(XmlBeanRefreshCommand.class);

    /**
     * path to spring xml
     */
    URL url;

    ClassLoader appClassLoader;

    public XmlBeanRefreshCommand(ClassLoader appClassLoader, URL url) {
        this.appClassLoader = appClassLoader;
        this.url = url;
    }

    @Override
    public void executeCommand() {
        if (!new File(url.getPath()).exists()) {
            LOGGER.trace("Skip Spring reload for delete event on file '{}'", url);
            return;
        }

        LOGGER.info("Executing XmlBeanDefinitionScannerAgent.reloadXml('{}')", url);

        try {
            // not using Class.getName() to get class name
            // because it will cause common classloader to load XmlBeanDefinationScannerAgent
            // which may cause problem in multi-app scenario.
            Class clazz = Class.forName("org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinationScannerAgent", true, appClassLoader);
            Method method  = clazz.getDeclaredMethod(
                    "reloadXml", new Class[] {URL.class});
            method.invoke(null, this.url);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Plugin error, maybe failed to hook spring method to init plugin with right classLoader", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, Spring class not found in application classloader", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing xml {} in classLoader {}", e, this.url, appClassLoader);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        XmlBeanRefreshCommand that = (XmlBeanRefreshCommand) o;

        return this.url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return this.url.hashCode();
    }

    @Override
    public String toString() {
        return "XmlBeanRefreshCommand{" +
                "url='" + url + '\'' +
                '}';
    }
}
