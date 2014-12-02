Hotswap Agent
=============
Java unlimited runtime class and resource redefinition.

The main purpose of this project is to avoid infamous change->restart + *wait*->check development lifecycle.
Save&Reload during development should be standard and many other languages (including C#) contain this feature.

This project is still in a beta version.

### Easy to start
Download and install latest [DCEVM Java patch](https://github.com/dcevm/dcevm/releases) + 
[agent jar](https://github.com/HotswapProjects/HotswapAgent/releases) and launch your application server 
with options `-XXaltjvm=dcevm -javaagent:hotswap-agent.jar` to get basic setup. 
Optionally add hotswap-agent.properties to your application to configure plugins and agent behaviour.

### Plugins
Each application framework (Spring, Hibernate, Logback, ...) needs special reloading mechanism to keep
up-to-date after class redefinition (e.g. Hibernate configuration reload after new entity class is introduced).
Hotswap agent works as a plugin system and ships preconfigured with all major framework plugins. It is easy
to write your custom plugin even as part of your application.

### Contribute
This project is very complex due to lot of supported frameworks and various versions. Community contribution
is mandatory to keep it alive. You can start by creating a plugin inside your application or by writing an
example/integration test. There is always need for documentation improvement :-). Thank you for any help!


Quick start:
===========
### Install
1. download [latest release of DCEVM Java patch](https://github.com/dcevm/dcevm/releases) and launch the installer 
(e.g. `java -jar installer-light.jar`). Currently you need to select correct installer for Java major version (7/8).
1. select java installation directory on your disc and press "Install DCEVM as altjvm" button. Java 1.7+ versions are supported.
1. download [latest release of Hotswap agent jar](https://github.com/HotswapProjects/HotswapAgent/releases), 
unpack `hotswap-agent.jar` and put it anywhere on your disc. For example: `C:\java\hotswap-agent.jar`

### Run your application
1. add following command line java attributes: `-XXaltjvm=dcevm -javaagent:PATH_TO_AGENT\hotswap-agent.jar` (you 
need to replace PATH_TO_AGENT with an actual) directory. For example `java -XXaltjvm=dcevm -javaagent:c:\java\hotswap-agent.jar YourApp`.
  See [IntelliJ IDEA](https://groups.google.com/forum/#!topic/hotswapagent/BxAK_Clniss)
  and [Netbeans](https://groups.google.com/forum/#!topic/hotswapagent/ydW5bQMwQqU) forum threads for IDE specific setup guides.
1. (optional) create a file named "hotswap-agent.properties" inside your resources directory, see available properties and
  default values: <https://github.com/HotswapProjects/HotswapAgent/blob/master/hotswap-agent-core/src/main/resources/hotswap-agent.properties>
1. start the application in debug mode, check that the agent and plugins are initialized correctly:

        HOTSWAP AGENT: 9:49:29.548 INFO (org.hotswap.agent.HotswapAgent) - Loading Hotswap agent - unlimited runtime class redefinition.
        HOTSWAP AGENT: 9:49:29.725 INFO (org.hotswap.agent.config.PluginRegistry) - Discovered plugins: [org.hotswap.agent.plugin.hotswapper.HotswapperPlugin, org.hotswap.agent.plugin.jvm.AnonymousClassPatchPlugin, org.hotswap.agent.plugin.hibernate.HibernatePlugin, org.hotswap.agent.plugin.spring.SpringPlugin, org.hotswap.agent.plugin.jetty.JettyPlugin, org.hotswap.agent.plugin.tomcat.TomcatPlugin, org.hotswap.agent.plugin.zk.ZkPlugin, org.hotswap.agent.plugin.logback.LogbackPlugin]
        ...
        HOTSWAP AGENT: 9:49:38.700 INFO (org.hotswap.agent.plugin.spring.SpringPlugin) - Spring plugin initialized - Spring core version '3.2.3.RELEASE'
1. save a changed resource and/or use the HotSwap feature of your IDE to reload changes

### What is available?
* Enhanced Java Hotswap - change method body, add/rename a method, field, ... The only unsupported operation
  is hierarchy change (change the superclass or remove an interface).
    * You can use standard Java Hotswap from IDE in debug mode to reload changed class 
    * or set autoHotswap property `-XXaltjvm=dcevm -javaagent:PATH_TO_AGENT\hotswap-agent.jar=autoHotswap=true` to reload
    changed classes after compilation. This setup allows even reload on production system without restart.
* Automatic configuration - all local classes and resources known to the running Java application are automatically
  discovered and watched for reload (all files on local filesystem, not inside JAR file).
* Extra classpath - Need change a runtime class inside dependent JAR? Use extraClasspath property to add any directory 
as a classpath to watch for class files.
* Reload resource after a change - resources from webapp directory are usually reloaded by application server. But what about
  other resources like src/main/resources? Use watchResources property to add any directory to watch for a resource change.
* Framework support - through plugin system, many frameworks are supported. New plugins can be easily added.
* Fast - until the plugin is initialized, it does not consume any resources or slow down the application (see Runtime overhead for more information)

Should you have any problems or questions, ask at [HotswapAgent forum](https://groups.google.com/forum/#!forum/hotswapagent).

This project is similar to [JRebel](http://zeroturnaround.com/software/jrebel/). Main differences are:

* HotswapAgent (DCEVM) supports Java8!
* HotswapAgent does not need any additional configuration for basic project setup.
* JRebel is currently more mature and contains more plugins.
* JRebel is neither open source nor free.
* JRebel modifies bytecode of all classes on reload. You need special IDE plugin to fix debugging.
* HotswapAgent extraClasspath is similar to JRebel <classpath> configuration 
* HotswapAgent adds watchResources configuration

### Examples
See [HotswapAgentExamples](https://github.com/HotswapProjects/HotswapAgentExamples) GitHub project.
The purpose of an example application is:

* complex automate integration tests (check various configurations before a release, see `run-tests.sh` script) 
* to check "real world" plugin usage during plugin development (i.e. inside container)
* to provide working solution for typical application setups
* sandbox to simulate issues for existing or new setups

Feel free to fork/branch and create an application for your setup (functional, but as simple as possible).
General setups will be merged into the master.

### IDE support
None needed :) Really, all changes are transparent and all you need to do is to download patch+agent and
setup your application / application server. Because we use standard java hotswap behaviour, your IDE will
work as expected. However, we work on IDE plugins to help with download & configuration.

Configuration
=============
The basic configuration set to reload classes and resources from classpath known to the running application
(classloader). If you need a different configuration, add hotswap-agent.properties file to the classpath root
(e.g. `src/main/resources/hotswap-agent.properties`).

Detail documentation of available properties and default values can be found in the [agent properties file](https://github.com/HotswapProjects/HotswapAgent/blob/master/hotswap-agent-core/src/main/resources/hotswap-agent.properties)

### Hotswap agent command line options
Full syntax of command line options is:

    -javaagent:[yourpath/]hotswap-agent.jar=[option1]=[value1],[option2]=[value2]

Hotswap agent accepts following options:

* autoHotswap=true - watch all .class files for change and automatically Hotswap the class in the running application
 (instead of running Hotswap from your IDE debugging session) 
* disablePlugin=[pluginName] - disable a plugin. Note that this will completely forbid the plugin to load 
    (opposite to disablePlugin option in hotswap-agent.properties, which will only disable the plugin for a classloader. 
    You can repeat this option for every plugin to disable.


How does it work?
=================

### DCEVM
Hotswap agent does the work of reloading resources and framework configuration (Spring, Hibernate, ...),
but it depends on standard Java hotswap mechanism to actually reload classes. Standard Java hotswap allows
only method body change , which makes it practically unusable. DCEVM is a JRE patch witch allows almost any
structural class change on hotswap (with an exception of a hierarchy change). Although hotswap agent works
even with standard java, we recommend to use DCEVM (and all tutorials use DCEVM as target JVM).

### Hotswap agent
Hotswap agent is a plugin container with plugin manager, plugin registry, and several agent services
(e.g. to watch for class/resource change). It helps with common tasks and classloading issues. It scans classpath
for class annotated with @Plugin annotation, injects agent services and registers reloading hooks. Runtime bytecode
modification is provided by javaasist library.

### Plugins
Plugins administered by Hotswap agent are usually targeted towards a specific framework. For example Spring plugin
uses agent services to:

* Modify root Spring classes to get Spring contexts and registered scan path
* Watch for any resource change on a scan path
* Watch for a hotswap of a class file within a scan path package
* Reload bean definition after a change
* ... and many other

Packaged plugins:

* Hibernate (4x) - Reload Hibernate configuration after entity create/change.
* Spring (3x) - Reload Spring configuration after class definition/change.
* Jetty - add extra classpath to the app classloader. All versions supporting WebAppContext.getExtraClasspath should be supported.
* ZK (5x-7x) - ZK Framework (http://www.zkoss.org/). Change library properties default values to disable caches, maintains Label cache and
bean resolver cache.
* Logback - Logback configuration reload
* Hotswapper - Watch for any class file change and reload (hotswap) it on the fly via Java Platform Debugger Architecture (JPDA)
* AnonymousClassPatch - Swap anonymous inner class names to avoid not compatible changes.
* ELResolver 2.2 (JuelEL, Appache Commons EL, Oracle EL 3.0)- clear ELResolver cache on class change. Support hotswap for #{...} expressions.
* Seam (2.2, 2.3) - flush JBoss reference cache. Support for properties file change (messages[])
* JSF (mojarra 2.1, 2.2) - support for application resource bundle files change (properties files).
* OsgiEquinox - Check class changes on extraClasspath and reload them in appropriate Equinox class loader. Support hotswap in Eclipse RCP.

Find a detail documentation of each plugin in the plugin project main README.md file.

### Runtime overhead
It really depends on how many frameworks you use and which caches are disabled. Example measurements
for a large, real world enterprise application based on Spring + Hibernate, run on Jetty.

    Setup                        | Startup time
    -----------------------------|-------------
    Run (plain Java)             | 23s
    Debug (plain Java)           | 24s
    Debug (plain DCEVM)          | 28s
    Agent - disabled all plugins | 31s
    Agent - all plugins          | 35s


How to write a plugin
=====================
You can write plugin directly as a part of your application. Set `pluginPackages=your.plugin.package` inside
your `hotswap-agent.properties` configuration to discover `@Plugin` annotated classes. You will also need
agent JAR dependency to compile, but be careful NOT to add the JAR to your application, it must be loaded only
as a javaagent. Maven dependency:

        <dependency>
            <groupId>org.hotswap.agent</groupId>
            <artifactId>HotswapAgent</artifactId>
            <version>${project.version}</version>
            <scope>provided</scope>
        </dependency>
(Note that the JAR is not yet in central maven repository - you need to build it from source first).

See [ExamplePlugin](https://github.com/HotswapProjects/HotswapAgentExamples/blob/master/SpringHibernate/src/main/java/org/hotswap/agent/it/plugin/ExamplePlugin.java)
(part of TestApplication) to go through a commented simple plugin. Read [agent readme](https://github.com/HotswapProjects/HotswapAgent/blob/master/README.md)
 to understand agent concepts. Check existing plugins source code for more examples.


Creating Release
================
Launch `run-tests.sh` script in the main directory. Currently you have to setup JAVA_HOME location directory manually. 
At least Java 7 and Java 8 with DCEVM should be checked before a release. All automatic tests are set to fail 
the whole script in case of any single test failure. 

Go to directory representing repository root. In case DCEVM is named `dcevm`

    mvn release:prepare
    mvn release:perform

In case your DCEVM is named differently i.e. `server`

    mvn release:prepare -Darguments="-Ddcevm=server"
    mvn release:perform -Darguments="-Ddcevm=server"

Plugin specific settings
========================

### OsgiEquinox / Eclipse RCP
OsgiEquinox plugin provides hotswap support for Eclipse plugin development in Eclipse RCP (Do not confuse it with common Eclipse development!). 
For hotswap in RUNTIME you will need to setup following options (debuggee Eclipse instance):

hotswap-agent.properties:

    extraClasspath=[your_development_classpath/]

eclipse.ini:

    -Dosgi.frameworkParentClassloader=app
    -Dosgi.dev=[your_development_classpath/]
    -XXaltjvm=dcevm
    -javaagent:PATH_TO_AGENT/hotswap-agent.jar

For hotswap while dubugging eclipse instance from another eclipse instance you will need to use the same RUNTIME option and additionaly use the following DEBUG options:
    
hotswap-agent.properties:

    osgiEquinox.debugMode=true

eclipse.ini

    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
    
then connect the debugger to port 8000 and happy hotswapping!

HotswapAgent writes all new/modified classes to extraClasspath in the DEBUG mode, so there is no need to manually copy them.

Credits
=======
Hotswap agent:

* Jiri Bubnik - project coordinator, initial implementation
* Vladimir Dvorak - Seam, ELResolver, JSF, OsgiEquinox plugins implementation
* Jan Tecl - web design

DCEVM:

* Ivan Dubrov - current project coordinator, update to Java7+Java8, patches, build system (Gradle)
* Thomas Würthinger - initial implementation.
* Kerstin Breitender – contributor.
* Christoph Wimberger – contributor.
