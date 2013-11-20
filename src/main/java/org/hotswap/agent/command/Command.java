package org.hotswap.agent.command;

import org.hotswap.agent.PluginManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command to run.
 *
 * @author Jiri Bubnik
 */
public class Command {
    private Object plugin;

    private String className;

    private String methodName;

    private ClassLoader targetClassLoader;

    private List<Object> params = new ArrayList<Object>();

    private CommandExecutionListener commandExecutionListener;

    public Command(Object plugin, String className, String methodName, ClassLoader targetClassLoader, Object... params) {
        this.plugin = plugin;
        this.className = className;
        this.methodName = methodName;
        this.targetClassLoader = targetClassLoader;
        this.params = Arrays.asList(params);
    }

    public Command(Object plugin, String className, String methodName) {
        this.plugin = plugin;
        this.className = className;
        this.methodName = methodName;
    }

    @Override
    public String toString() {
        return "Command{" +
                "className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                '}';
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<Object> getParams() {
        return params;
    }

    public ClassLoader getTargetClassLoader() {
        if (targetClassLoader == null)
            targetClassLoader = PluginManager.getInstance().getPluginRegistry().getAppClassLoader(plugin);

        return targetClassLoader;
    }

    public void setTargetClassLoader(ClassLoader targetClassLoader) {
        this.targetClassLoader = targetClassLoader;
    }

    public CommandExecutionListener getCommandExecutionListener() {
        return commandExecutionListener;
    }

    public void setCommandExecutionListener(CommandExecutionListener commandExecutionListener) {
        this.commandExecutionListener = commandExecutionListener;
    }
}
