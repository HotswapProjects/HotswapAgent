package org.hotswap.agent.command;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.logging.AgentLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command to run in a target classloader. The command is always invoked via reflection.
 *
 * @author Jiri Bubnik
 */
public class ReflectionCommand extends MergeableCommand {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ReflectionCommand.class);

    /**
     * Run the method on target object.
     */
    private Object target;

    /**
     * Run a method in the class - if null, run a method on this object, otherwise create new instance of className.
     */
    private String className;

    /**
     * Method name to run.
     */
    private String methodName;

    /**
     * Method actual parameters value. These parameter types must be known to the target classloader.
     * For norma use (call application classloader from plugin class) this means that you can use only
     * Java default types.
     */
    private List<Object> params = new ArrayList<Object>();

    /**
     * Plugin object to resolve target classloader (if not set directly). May be null.
     */
    private Object plugin;

    /**
     * Classloader application classloader to run the command in. If null, use agent classloader.
     */
    private ClassLoader targetClassLoader;

    /**
     * Register a listener after the command is executed to obtain method invocation result.
     */
    private CommandExecutionListener commandExecutionListener;

    /**
     * Define a command.
     */
    public ReflectionCommand(Object plugin, String className, String methodName, ClassLoader targetClassLoader, Object... params) {
        this.plugin = plugin;
        this.className = className;
        this.methodName = methodName;
        this.targetClassLoader = targetClassLoader;
        this.params = Arrays.asList(params);
    }

    /**
     * Predefine a command. The params and/or target classloader will be set by setter.
     */
    public ReflectionCommand(Object plugin, String className, String methodName) {
        this.plugin = plugin;
        this.className = className;
        this.methodName = methodName;
    }

    /**
     * Define a command on target object.
     */
    public ReflectionCommand(Object target, String methodName, Object... params) {
        this.target = target;
        this.methodName = methodName;
        this.params = Arrays.asList(params);
    }


    @Override
    public String toString() {
        return "Command{" +
                "class='" + getClassName() + '\'' +
                ", methodName='" + getMethodName() + '\'' +
                '}';
    }

    public String getClassName() {
        if (className == null && target != null)
            className = target.getClass().getName();

        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<Object> getParams() {
        return params;
    }

    public ClassLoader getTargetClassLoader() {
        if (targetClassLoader == null) {
            if (target != null)
                targetClassLoader = target.getClass().getClassLoader();
            else
                targetClassLoader = PluginManager.getInstance().getPluginRegistry().getAppClassLoader(plugin);
        }

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

    /**
     * Execute the command.
     */
    public void executeCommand() {
        // replace context classloader with application classloader
        if (getTargetClassLoader() != null)
            Thread.currentThread().setContextClassLoader(getTargetClassLoader());

        ClassLoader targetClassLoader = Thread.currentThread().getContextClassLoader();

        String className = getClassName();
        String method = getMethodName();
        List<Object> params = getParams();

        Object result = null;
        try {
            result = doExecuteReflectionCommand(targetClassLoader, className, target, method, params);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Class {} not found in classloader {}", e, className, targetClassLoader);
        } catch (NoClassDefFoundError e) {
            LOGGER.error("NoClassDefFoundError for class {} in classloader {}", e, className, targetClassLoader);
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
        CommandExecutionListener listener = getCommandExecutionListener();
        if (listener != null)
            listener.commandExecuted(result);
    }

    protected Object doExecuteReflectionCommand(ClassLoader targetClassLoader, String className, Object target, String method, List<Object> params) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        Class<?> classInAppClassLoader = Class.forName(className, true, targetClassLoader);

        LOGGER.trace("Executing command: requestedClassLoader={}, resolvedClassLoader={}, class={}, method={}, params={}",
                targetClassLoader, classInAppClassLoader.getClassLoader(), classInAppClassLoader, method, params);

        Class[] paramTypes = new Class[params.size()];
        int i = 0;
        for (Object param : params) {
            if (params == null)
                throw new IllegalArgumentException("Cannot execute for null parameter value");
            else {
                paramTypes[i++] = param.getClass();
            }
        }

        Method m = classInAppClassLoader.getDeclaredMethod(method, paramTypes);

        return m.invoke(target, params.toArray());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReflectionCommand)) return false;

        ReflectionCommand that = (ReflectionCommand) o;

        if (!className.equals(that.className)) return false;
        if (!methodName.equals(that.methodName)) return false;
        if (!params.equals(that.params)) return false;
        if (plugin != null ? !plugin.equals(that.plugin) : that.plugin != null) return false;
        if (target != null ? !target.equals(that.target) : that.target != null) return false;
        if (targetClassLoader != null ? !targetClassLoader.equals(that.targetClassLoader) : that.targetClassLoader != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = target != null ? target.hashCode() : 0;
        result = 31 * result + className.hashCode();
        result = 31 * result + methodName.hashCode();
        result = 31 * result + params.hashCode();
        result = 31 * result + (plugin != null ? plugin.hashCode() : 0);
        result = 31 * result + (targetClassLoader != null ? targetClassLoader.hashCode() : 0);
        return result;
    }
}
