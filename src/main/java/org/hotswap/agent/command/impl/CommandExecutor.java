package org.hotswap.agent.command.impl;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.CommandExecutionListener;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Execute a command.
 *
 * @author Jiri Bubnik
 */
public class CommandExecutor extends Thread {
    private static AgentLogger LOGGER = AgentLogger.getLogger(CommandExecutor.class);

    final Command command;

    public CommandExecutor(Command command) {
        this.command = command;
        setDaemon(true);
        if (command.getTargetClassLoader() != null)
            setContextClassLoader(command.getTargetClassLoader());
    }

    @Override
    public void run() {
        try {
            executeCommand();
        } finally {
            finished();
        }
    }

    /**
     * Method template to register finish event
     */
    public void finished() {
    }


    protected void executeCommand() {
        ClassLoader targetClassLoader = Thread.currentThread().getContextClassLoader();
        String className = command.getClassName();
        String method = command.getMethodName();
        List<Object> params = command.getParams();

        Object result = null;
        try {
            result = doExecuteCommand(targetClassLoader, className, method, params);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Class {} not found in classloader {}", e, className, targetClassLoader);
        } catch (InstantiationException e) {
            LOGGER.error("Unable instantiate class {} in classloader {}", e, className, targetClassLoader);
        } catch (IllegalAccessException e) {
            LOGGER.error("Method {} not public in class {}", e, method, className);
        } catch (NoSuchMethodException e) {
            LOGGER.error("Method {} not found in class {}", e, method, className);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error executin method {} in class {}", e, method, className);
        }

        // notify lilstener
        CommandExecutionListener listener = command.getCommandExecutionListener();
        if (listener != null)
            listener.commandExecuted(result);
    }

    private Object doExecuteCommand(ClassLoader targetClassLoader, String className, String method, List<Object> params) throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Class classInAppClassLoader = Class.forName(className, true, targetClassLoader);

        LOGGER.debug("Executing: requestedClassLoader={}, resolvedClassLoader={}, class={}, method={}, params={}",
                targetClassLoader, classInAppClassLoader.getClassLoader(), classInAppClassLoader, method, params);

        Class[] paramTypes = new Class[params.size()];
        int i = 0;
        for (Object param : params) {
            if (params == null)
                throw new IllegalArgumentException("Cannot execute for null parameter value");
            else if (param.getClass().getClassLoader() != null &&
                    param.getClass().getClassLoader() != targetClassLoader) {
                throw new IllegalArgumentException("Parameter not from target classloader.");
            } else {
                paramTypes[i++] = param.getClass();
            }
        }

        Object instance = classInAppClassLoader.newInstance();
        Method m = classInAppClassLoader.getDeclaredMethod(method, paramTypes);

        return m.invoke(instance, params.toArray());
    }
}
