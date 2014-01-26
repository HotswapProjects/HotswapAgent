package org.hotswap.agent.it.plugin;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.annotation.Watch;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.watch.WatchEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

/**
 * The plugin system annotation is similar to Spring MVC way - use method annotation with variable
 * method attributes types. See each annotation javadoc for available attribute types and usage.
 * <p/>
 * Always be aware of which classloader your code use (Application or agent classloader?) More on
 * classloader issues in
 * <a href="https://github.com/HotswapProjects/HotswapAgent/blob/master/HotswapAgent/README.md">Agent documentation</a>
 */
@Plugin(name = "ExamplePlugin", description = "Hotswap agent plugin as part of normal application.",
        testedVersions = "Describe dependent framework version you have tested the plugin with.",
        expectedVersions = "Describe dependent framework version you expect to work the plugin with.")
public class ExamplePlugin {

    // as an example, we will enhance this service to return content of examplePlugin.resource
    // and class load/reload counts in it's helloWorld service method
    public static final String TEST_ENTITY_SERVICE = "org.hotswap.agent.it.service.TestEntityService";

    // Agent logger is a very simple custom logging mechanism. Do not use any common logging framework
    // to avoid compatibility and classloading issues.
    private static AgentLogger LOGGER = AgentLogger.getLogger(ExamplePlugin.class);

    /**
     * Any plugin has to have at least one static @Transform method to hook initialization code. It is usually
     * some key framework method. Call PluginManager.initializePlugin() to create new plugin instance and
     * initialize it with the application classloader. Than call one or more methods on the plugin
     * to pass reference to framework/application objects.
     *
     * @param ctClass see @Transform javadoc for available parameter types. CtClass is convenient way
     *                to enhance method bytecode using javaasist
     */
    @Transform(classNameRegexp = TEST_ENTITY_SERVICE)
    public static void transformTestEntityService(CtClass ctClass) throws NotFoundException, CannotCompileException {

        // You need always find a place from which to initialize the plugin.
        // Initialization will create new plugin instance (notice that transformTestEntityService is
        // a static method), inject agent services (@Inject) and register event listeners (@Transform and @Watch).
        String src = PluginManagerInvoker.buildInitializePlugin(ExamplePlugin.class);

        // If you need to call a plugin method from application context, there are some issues
        // Always think about two different classloaders - application and agent/plugin. The parameter
        // here cannot be of type TestEntityService because the plugin does not know this type at runtime
        // (although it will compile here!). If you call plugin method, usually only basic java types (java.lang.*)
        // are safe.
        src += PluginManagerInvoker.buildCallPluginMethod(ExamplePlugin.class, "registerService", "this", "java.lang.Object");

        // do enhance default constructor using javaasist. Plugin manager (TransformHandler) will use enhanced class
        // to replace actual bytecode.
        ctClass.getDeclaredConstructor(new CtClass[0]).insertAfter(src);

        LOGGER.debug(TEST_ENTITY_SERVICE + " has been enhanced.");
    }

    /**
     * All compiled code in ExamplePlugin is executed in agent classloader and cannot access
     * framework/application classes. If you need to call a method on framework class, use application
     * classloader. It is injected on plugin initialization.
     */
    @Init
    ClassLoader appClassLoader;

    /**
     * Called from TEST_ENTITY_SERVICE enhanced constructor. Note that the service cannot be typed to
     * TEST_ENTITY_SERVICE class - the class is not known to agent classloader (it lives only in the
     * application classloader).
     */
    public void registerService(Object testEnityService) {
        this.testEnityService = testEnityService;
        LOGGER.info("Plugin {} initialized on service {}", getClass(), testEnityService);
    }
    // the service
    Object testEnityService;


    // Scheduler service - use to run a command asynchronously and merge multiple similar commands to one execution
    // static  - Scheduler and other agent services are available even in static context (before the plugin is initialized)
    @Init
    Scheduler scheduler;


    /**
     * Use @Watch annotation to register resource event listener (using file NIO events). See @Watch javadoc
     * for available method parameter types.
     */
    @Watch(path = "examplePlugin.resource")
    public void watchForResourceChange(URI uri) throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, NotFoundException, CannotCompileException {
        // simple example - read new value and set it via reflection
        String examplePluginResourceText = new String(IOUtils.toByteArray(uri));

        Method setExamplePluginResourceText = appClassLoader.loadClass(TEST_ENTITY_SERVICE)
                .getDeclaredMethod("setExamplePluginResourceText", String.class);
        setExamplePluginResourceText.invoke(testEnityService, examplePluginResourceText);

        LOGGER.info("Service examplePluginResourceText value changed to {}", examplePluginResourceText);
    }
    int reloadedClasses = 0;

    /**
     * Count how many classes were loaded after the plugin is initialized (after TEST_ENTITY_SERVICE constructor).
     */
    @Transform(classNameRegexp = "org.hotswap.agent.it.*", onReload = false)
    public void loadClass() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // you can use scheduler to run framework method asynchronously
        // there is convenient ReflectionCommand, which runs method in the target classloader, the reflection
        // logic is similar to code in watchForResourceChange (see ReflectionCommand source).
        scheduler.scheduleCommand(
                new ReflectionCommand(testEnityService, "setLoadedClasses", ++reloadedClasses)
        );
    }

    /**
     * Count how many classes were reloaded via hotswap after the plugin is initialized.
     *
     * (Note - if you test the behaviour, do not hotswap the TestEntityService itself - it will call itself
     *  and hang. If you change TestRepository, the TestEntityService will be recreated as well - try it).
     */
    @Transform(classNameRegexp = ".*", onDefine = false)
    public void reloadClass(String className) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // run the logic in a command. Multiple reload commands may be merged into one execution (see the command class)
        scheduler.scheduleCommand(new ReloadClassCommand(appClassLoader, className, testEnityService));
    }

    /**
     * Watch for .class creation. This is quite common if you use classpath scanning frameworks (Spring, Hibernate, ..)
     * New class file is not known to the framework classloading process and hence not processed until instructed
     * explicitly by the plugin.
     * One caveat - although only CREATE type is watched for, compile process will generate DELETE/CREATE sequence
     * for modification.
     */
    @Watch(path = "org/hotswap/agent/it", filter = ".*.class", watchEvents = {WatchEvent.WatchEventType.CREATE})
    public void changeClassFile(URI file) {
        String path = file.getPath();
        // strip of path prefix (before package name)
        path = path.substring(file.getPath().indexOf("org/hotswap/agent/it"));
        // strip of .class suffix
        String className = path.substring(0, path.indexOf(".class"));

        // Schedule command with longer timeout to wait for other similar commands and merge them into single event
        scheduler.scheduleCommand(new ReloadClassCommand(appClassLoader, className, testEnityService), 500);
    }
}
