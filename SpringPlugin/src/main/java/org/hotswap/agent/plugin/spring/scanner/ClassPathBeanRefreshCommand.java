package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

/**
 * Do refresh Spring class (scanned by classpath scanner) based on URI or byte[] definition.
 *
 * This commands merges events of watcher.event(CREATE) and transformer hotswap reload to a single refresh command.
 */
public class ClassPathBeanRefreshCommand implements Command {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassPathBeanRefreshCommand.class);

    ClassLoader appClassLoader;

    String basePackage;

    String className;

    byte[] classDefinition;

    public ClassPathBeanRefreshCommand(ClassLoader appClassLoader, String basePackage, String className, byte[] classDefinition) {
        this.appClassLoader = appClassLoader;
        this.basePackage = basePackage;
        this.className = className;
        this.classDefinition = classDefinition;
    }

    public ClassPathBeanRefreshCommand(ClassLoader appClassLoader, String basePackage, URI uri) {
        this.appClassLoader = appClassLoader;
        this.basePackage = basePackage;

        // strip from URI prefix up to basePackage and .class suffix.
        this.className = uri.getPath().replaceAll("^.*" + basePackage + ".", "").replaceAll(".class$", "");

        this.classDefinition = IOUtils.toByteArray(uri);
    }

    @Override
    public void executeCommand() {
        try {
            Method method  = ClassPathBeanDefinitionScannerAgent.class.getDeclaredMethod(
                    "refreshClass", new Class[] {String.class, byte[].class});
            method.invoke(null, basePackage, classDefinition);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Plugin error, method not found", e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error refreshing class {} in classLoader {}", e, className, appClassLoader);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Plugin error, illegal access", e);
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClassPathBeanRefreshCommand that = (ClassPathBeanRefreshCommand) o;

        if (!appClassLoader.equals(that.appClassLoader)) return false;
        if (!className.equals(that.className)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appClassLoader.hashCode();
        result = 31 * result + className.hashCode();
        return result;
    }
}
