package org.hotswap.agent.plugin.spring.scanner;

import org.hotswap.agent.command.ReflectionCommand;

/**
 * Standard reflection command, but group same command by basePath and resource.
 */
@Deprecated
public class ClassPathScannerCommand extends ReflectionCommand {
    String basePath;
    byte[] classDefinition;

    public ClassPathScannerCommand(Object plugin, String className, String methodName, ClassLoader targetClassLoader,
                                   String basePath, byte[] classDefinition) {
        super(plugin, className, methodName, targetClassLoader, basePath, classDefinition);
        this.basePath = basePath;
        this.classDefinition = classDefinition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClassPathScannerCommand that = (ClassPathScannerCommand) o;

        if (!basePath.equals(that.basePath)) return false;
        if (!classDefinition.equals(that.classDefinition)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = basePath.hashCode();
        result = 31 * result + classDefinition.hashCode();
        return result;
    }
}
