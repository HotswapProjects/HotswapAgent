Hotswap Agent Implementation
============================

Hotswap agent is a plugin container with plugin manager, plugin registry, and several services
(e.g. to watch for class/resource change). It helps with common tasks and classloading issues.

Agent structure
---------------
The agent is initialized as a javaagent (at JVM startup see META-INF/MANIFEST.MF registration of `HotswapAgent.premain()`, when JVM running see META-INF/MANIFEST.MF registration of `HotswapAgent.agentmain()`). This in turn
initialize the singleton agent class `PluginManager`. You can always use `PluginManager.getInstance()` to access the agent,
although it is usually better to use plugin custom "dependency injection" via @Init annotation to access agent services.

* PluginManager - The main agent plugin manager, well known singleton controller.
* PluginRegistry - Registry of all known plugins. For each plugin class it contains a Map(application classloader -> plugin instance)
* PluginConfiguration - read hotswap-agent.properties for each application classloader, configure agent/plugins behaviour
* Annotations (@Plugin, @Init, @Transform, @Watch) - define agent plugin and wire / use agent services
* Scheduler / Command - schedule an command for execution. Merge similar commands into single execution, handle
    classloading issues.
* Watcher - service to register filesystem events (create/modify/delete a resource).
* Transformer - service to register hotwap transformation (define or reload a class definition).
* Javaassist - source code for 3.17.1-GA official javaasist release repackaged to custom location to avoid conflicts.
* Logger - Simple logging mechanism. Custom logging is use instead of library to avoid conflicts with frameworks / app servers.

Classloading issues
-------------------
If you have more than one classloader (typically in a servlet container (jetty/tomcat) or application server
(jboss, Weblogic, ...)), there is always an issue of parent/child precedence and class visibility. Maybe you
 have encountered weired ClassCastException on the same class or NoClassDefFound even when the class was
 on a classpath. The answer is, that unique java class is it's name AND the classloader.

Hotswap agent is always loaded by the main application server classloader. However, the frameworks are usually
 in an application classloader (if you include them in a WAR file). Hence the hotswap agent is not able to
 call directly any framework class, because it is in the child classloader and it is not visible to it. The agent
 classes are visible to the framework, so if you enhance a framework method with agent call and pass it's object
 to a plugin, it may use reflection to call a method on the object (getClass() on the object will return class
 from application classloader). It is not convenient for more complex actions though (lot of reflection).

Hotswap agent solve this issue by copying all plugin classes in BOTH classloaders - agent and application. You should
be always aware of the classloader currently in use (provided plugins contain javadoc with information
for which classloader is each class targeted). The main @Plugin class itself always run in the agent classloader.
Typically you will use commands (e.g. ReflectionCommand) to run a method on a class in application classloader. Than
it can safely access all framework classes. Note that the agent classes (e.g. PluginManager) must NOT be in the
application classloader.

* The framework / your application classloader is in the code referred as "app classloader".
* The startup / agent / main / container classloader is in the code referred as "main classloader" or "agent classloader".

The plugin manger does not know about the framework classloader automatically. Each plugin must hook into some framework
method and call PluginManager.initPlugin() method. Usually this is done by PluginManagerInvoker.buildInitializePlugin()
method. If you need to call a plugin method, it is even more complicated. Remember that the plugin class is defined
in both classloaders (agent + app) and if you are in the app classloader, you need to use reflection to call the plugin.
There is convenience builder PluginManagerInvoker.buildCallPluginMethod() to achieve this.

Plugin configuration
====================
PluginRegistry:
* scanPlugins(ClassLoader classLoader, String pluginPackage) - search for plugin classes (looking for @Plugin annotation) and
    process annotations
* initializePlugin(String pluginClass, ClassLoader appClassLoader) - initialize and register a plugin instance in an classloader
* getPlugin(Class<T> pluginClass, ClassLoader classLoader) - return plugin instance registered in a classloader
* getAppClassLoader(Object plugin) - return an application classloader of a plugin instance

