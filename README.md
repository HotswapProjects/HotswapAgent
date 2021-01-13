Hotswap Agent
=============

<p align="left">
    <a href="https://mvnrepository.com/artifact/org.hotswapagent/hotswap-agent-core">
        <img src="https://img.shields.io/maven-central/v/org.hotswapagent/hotswap-agent-core.svg" alt="Maven">
    </a>
    <a href="https://travis-ci.org/HotswapProjects/HotswapAgent">
        <img src="https://travis-ci.org/HotswapProjects/HotswapAgent.svg?branch=master" alt="Build Status">
    </a>
    <a href="https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html">
        <img src="https://img.shields.io/badge/License-GPL%20v2-blue.svg" alt="License: GPL v2">
    </a>
    <a href="https://gitter.im/HotswapProjects/user">
        <img src="https://badges.gitter.im/Join%20Chat.svg" alt="Gitter">
    </a>
    <a href='https://sourcespy.com/github/hotswapprojectshotswapagent/' title='SourceSpy Dashboard'>
        <img src='https://sourcespy.com/shield.svg'/>
    </a>
    <a href="https://twitter.com/intent/follow?screen_name=HSwapAgent">
        <img src="https://img.shields.io/twitter/follow/HSwapAgent.svg?style=social&logo=twitter" alt="follow on Twitter">
    </a>
</p>

This is an overview page, please visit [hotswapagent.org](http://hotswapagent.org/) for more information.

### Overview
***

Java unlimited runtime class and resource redefinition.

Originally, the main purpose of this project was to avoid the infamous **change code**->**restart and wait...**->**check development** lifecycle. Lately, this schema evolved into a new paradigm in the Java world, based on the development of software in running application, that approach can be used even in a closed environment like Docker.

### Easy to start
Download and install latest [jdk8-dcevm](https://github.com/dcevm/dcevm/releases) + [HotswapAgent](https://github.com/HotswapProjects/HotswapAgent/releases) or download [jdk11-dcevm with integrated HotswapAgent](https://github.com/TravaOpenJDK/trava-jdk-11-dcevm/releases) and install it as an alternative JDK. In the case of dcevm8, launch your application with options `-XXaltjvm=dcevm -javaagent:hotswap-agent.jar` to get a basic setup. Optionally you can add `hotswap-agent.properties` to your application to configure plugins and agent's behavior.

### Plugins
Each application framework (Spring, Hibernate, Logback, ...) needs a special reloading mechanism to keep
up-to-date after class redefinition (e.g. Hibernate configuration reload after new entity class is introduced).
Hotswap agent works as a plugin system and is shipped preconfigured with all major framework plugins. It is easy
to write your custom plugin even as part of your application.

### Contribute
This project is very complex due to a lot of supported frameworks and various versions. Community contribution
is mandatory to keep it alive. You can start by creating a plugin inside your application or by writing an
example/integration test. There is always a need for documentation improvement :-). Thank you for any help!

The [sourcespy dashboard](https://sourcespy.com/github/hotswapprojectshotswapagent/) provides a high-level overview of the repository including [module dependencies](https://sourcespy.com/github/hotswapprojectshotswapagent/xx-omodulesc-.html), [module hierarchy](https://sourcespy.com/github/hotswapprojectshotswapagent/xx-omodules-.html), [external libraries](https://sourcespy.com/github/hotswapprojectshotswapagent/xx-ojavalibs-.html), and other components of the system.

### Issue triage [![Open Source Helpers](https://www.codetriage.com/hotswapprojects/hotswapagent/badges/users.svg)](https://www.codetriage.com/hotswapprojects/hotswapagent)

You can contribute by triaging issues which may include reproducing bug reports or asking for vital information, such as version numbers or reproduction instructions. If you would like to start triaging issues, one easy way to get started is to [subscribe to hotswapagent on CodeTriage](https://www.codetriage.com/hotswapprojects/hotswapagent).

Quick start:
===========
### Install
1. download [latest release of DCEVM Java patch](https://github.com/dcevm/dcevm/releases) and launch the installer
(e.g. `java -jar installer-light.jar`). Currently, you need to select the correct installer for Java major version (7/8).
1. select java installation directory on your disc and press "Install DCEVM as altjvm" button. Java 1.7+ versions are supported.
1. download [latest release of Hotswap agent jar](https://github.com/HotswapProjects/HotswapAgent/releases),
unpack `hotswap-agent.jar` and put it anywhere on your disc. For example: `C:\java\hotswap-agent.jar`

### Run your application
1. add following command line java attributes: `-XXaltjvm=dcevm -javaagent:PATH_TO_AGENT\hotswap-agent.jar` (you
need to replace PATH_TO_AGENT with an actual) directory. For example `java -XXaltjvm=dcevm -javaagent:c:\java\hotswap-agent.jar YourApp`.
  See [IntelliJ IDEA](https://groups.google.com/forum/#!topic/hotswapagent/BxAK_Clniss)
  and [Netbeans](https://groups.google.com/forum/#!topic/hotswapagent/ydW5bQMwQqU) forum threads for IDE specific setup guides. If your application is already running, you still can attach the agent jar using the example [code snippet](https://gist.github.com/xnike/a268fc209df52bf1bf09a268e97cef53).
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
    changed classes after compilation. This setup allows even reload on a production system without a restart.
* Automatic configuration - all local classes and resources, known to the running Java application, are automatically
  discovered and watched for the reload (all files on the local filesystem, not inside the any JAR file).
* Extra classpath - Need change a runtime class inside dependent JAR? Use extraClasspath property to add any directory
as a classpath to watch for class files.
* Reload resource after a change - resources from the webapp directory are usually reloaded by the application server. But what about
  other resources like src/main/resources? Use watchResources property to add any directory to watch for a resource change.
* Framework support - through plugin-system, many frameworks are supported. New plugins can be easily added.
* Fast - until the plugin is initialized, it does not consume any resources or slow down the application (see Runtime overhead for more information)

Should you have any problems or questions, ask at [HotswapAgent forum](https://groups.google.com/forum/#!forum/hotswapagent).

This project is similar to [JRebel](http://zeroturnaround.com/software/jrebel/). The main differences are:

* HotswapAgent (DCEVM) supports Java8!
* HotswapAgent does not need any additional configuration for basic project setup.
* JRebel is currently more mature and contains more plugins.
* JRebel is neither open source nor free.
* JRebel modifies bytecode of all classes on reload. You need a special IDE plugin to fix debugging.
* HotswapAgent extraClasspath is similar to JRebel <classpath> configuration
* HotswapAgent adds watchResources configuration

### Examples
See [HotswapAgentExamples](https://github.com/HotswapProjects/HotswapAgentExamples) GitHub project.
The purpose of an example application is:

* complex automate integration tests (check various configurations before a release, see `run-tests.sh` script)
* to check "real world" plugin usage during plugin development (i.e. inside a container)
* to provide a working solution for typical application setups
* sandbox to simulate issues for existing or new setups

Feel free to fork/branch and create an application for your setup (functional, but as simple as possible).
General setups will be merged into the master.

### IDE support
None needed :) Really! All changes are transparent and all you need to do is to download patch+agent and
setup your application/application server. Because we use standard java hotswap behaviour, your IDE will
work as expected. However, we work on IDE plugins to help with download & configuration.

Configuration
=============
The basic configuration is set to reload classes and resources from the classpath known to the running application
(classloader). If you need a different configuration, add the hotswap-agent.properties file to the classpath root
(e.g. `src/main/resources/hotswap-agent.properties`).

Detail documentation of available properties and default values can be found in the [agent properties file](https://github.com/HotswapProjects/HotswapAgent/blob/master/hotswap-agent-core/src/main/resources/hotswap-agent.properties)

### Hotswap agent command-line options
Full syntax of command line options is:

    -javaagent:[yourpath/]hotswap-agent.jar=[option1]=[value1],[option2]=[value2]

Hotswap agent accepts the following options:

* autoHotswap=true - watch all .class files for change and automatically Hotswap the class in the running application
 (instead of running Hotswap from your IDE debugging session)
* disablePlugin=[pluginName] - disable a plugin. Note that this will completely forbid the plugin to load
    (opposite to disablePlugin option in hotswap-agent.properties, which will only disable the plugin for a classloader.
    You can repeat this option for every plugin to disable.


How does it work?
=================

### DCEVM
Hotswap agent does the work of reloading resources and framework configuration (Spring, Hibernate, ...),
but it depends on the standard Java hotswap mechanism to reload classes. Standard Java hotswap allows
only method body change, which makes it practically unusable. DCEVM is a JRE patch that allows almost any
structural class change on hotswap (with an exception of a hierarchy change). Although hotswap agent works
even with standard java, we recommend using DCEVM (and all tutorials use DCEVM as target JVM).

### Hotswap agent
Hotswap agent is a plugin container with plugin manager, plugin registry, and several agent services
(e.g. to watch for class/resource change). It helps with common tasks and classloading issues. It scans the classpath
for class annotated with @Plugin annotation, injects agent services, and registers reloading hooks. Runtime bytecode
modification is provided by javaasist library.

### Plugins
Plugins administered by Hotswap agent are usually targeted towards a specific framework. For example Spring plugin
uses agent services to:

* Modify root Spring classes to get Spring contexts and registered scan path
* Watch for any resource change on a scan path
* Watch for a hotswap of a class file within a scan path package
* Reload bean definition after a change
* ... and many other

#### Java frameworks plugins:

* [CXF-JAXRS](plugin/hotswap-agent-cxf-plugin/README.md) (3.x) - redefine JAXRS resource after resource class redefinition, reinject instance if integrated with Spring and CDI (Weld/OWB).
* [Deltaspike](plugin/hotswap-agent-deltaspike-plugin/README.md) (1.x) - messages, ViewConfig, repository, proxy reloading. Deltaspike scoped CDI beans reinjection.
* [ELResolver](plugin/hotswap-agent-el-resolver-plugin/README.md) 2.2 (JuelEL, Appache Commons EL, Oracle EL 3.0)- clear ELResolver cache on class change. Support hotswap for #{...} expressions.
* [FreeMarker](plugin/hotswap-agent-freemarker-plugin/README.md) - clear the Apache Freemarker beans class-introspection cache on class definition change.
* [Hibernate](plugin/hotswap-agent-hibernate-plugin/README.md) (3x,4x,5x) - Reload Hibernate configuration after entity create/change.
* [Jersey1](plugin/hotswap-agent-jersey1-plugin/README.md) - reload Jersey1 container after root resource or provider class definition or redefinition.
* [Jersey2](plugin/hotswap-agent-jersey2-plugin/README.md) - reload Jersey2 container after root resource or provider class definition or redefinition.
* [Logback](plugin/hotswap-agent-logback-plugin/README.md) - Logback configuration reload.
* [Log4j2](plugin/hotswap-agent-log4j2-plugin/README.md) - Log4j2 configuration reload.
* [Mojarra](plugin/hotswap-agent-mojarra-plugin/README.md) (2.1,2.2) - support for application resource bundle changes (properties files). Support for ViewScoped beans reinjection/reloading.
* [MyBatis](plugin/hotswap-agent-mybatis-plugin/README.md) (5.3) - reload configuration after mapper file changes
* [MyFaces](plugin/hotswap-agent-myfaces-plugin/README.md) (2.2) - support for application resource bundle changes (properties files). Support for ViewScoped beans reinjection/reloading.
* [OmniFaces](plugin/hotswap-agent-owb-plugin/README.md) - support for ViewScoped beans reinjection/reloading.
* [OpenWebBeans](plugin/hotswap-agent-owb-plugin/README.md) - (CDI) (1.x, 2.x) - reload bean class definition after class definition/change. Beans can be reloaded according strategy defined in property file.
* [OsgiEquinox](plugin/hotswap-agent-osgiequinox-plugin/README.md) - Hotswap support for Eclipse plugin or Eclipse platform development.
* [RestEasy](plugin/hotswap-agent-resteasy-registry-plugin/README.md) (2.x, 3.x) - Cleanups and registers class redefinitions.
* [Spring](plugin/hotswap-agent-spring-plugin/README.md) (3x, 4.x, 5.x) - Reload Spring configuration after class definition/change.
* [Vaadin](plugin/hotswap-agent-vaadin-plugin/README.md) (10.x) - Update routes, template models and in practice anything on the fly.
* [WebObjects](plugin/hotswap-agent-webobjects-plugin/README.md) - Clear key value coding, component, action and validation caches after class change.
* [Weld](plugin/hotswap-agent-weld-plugin/README.md) (CDI) (2.2,2.3,3.x) - reload bean class definition after class definition/change. Beans can be reloaded according strategy defined in property file.
* [Wicket](plugin/hotswap-agent-wicket-plugin/README.md) - clear wicket caches if property files are changed
* [WildFlyELResolver](plugin/hotswap-agent-wildfly-el-plugin/README.md) - Clear BeanELResolver after any class redefinition.
* [ZK](plugin/hotswap-agent-zk-plugin/README.md) (5x-7x) - ZK Framework (http://www.zkoss.org/). Change library properties default values to disable caches, maintains Label cache and bean resolver cache.

#### Servlet containers and application servers plugins:

* [JBossModules](plugin/hotswap-agent-jbossmodules-plugin/README.md) - add extra class path to JBoss's module class loader. (Wildfly)
* [Jetty](plugin/hotswap-agent-jetty-plugin/README.md) - add extra classpath to the app classloader. All versions supporting WebAppContext.getExtraClasspath should be supported.
* [Tomcat](plugin/hotswap-agent-tomcat-plugin/README.md) (7.x, 8.x) configure Apache Tomcat with extraClasspath and webApp property. Supports also GlassFish, Payara and Tomee7.
* [Undertow](plugin/hotswap-agent-undertow-plugin/README.md) - add extra classpath, watchResources and webappDir to the undertow's resource manager.

#### JVM plugins - hotswapping enhancements:

* [AnonymousClassPatch](hotswap-agent-core/src/main/java/org/hotswap/agent/plugin/jvm/README.md) - Swap anonymous inner class names to avoid not compatible changes.
* [ClassInit](hotswap-agent-core/src/main/java/org/hotswap/agent/plugin/jvm/README.md) - initializes new static members/enum values after class/enum redefinition and keeps surviving static values. (Fix of known DCEVM limitation)
* [Hotswapper](hotswap-agent-core/src/main/java/org/hotswap/agent/plugin/hotswapper/README.md) - Watch for any class file change and reload (hotswap) it on the fly via Java Platform Debugger Architecture (JPDA)
* [Proxy](plugin/hotswap-agent-proxy-plugin/README.md) (supported com.sun.proxy, CGlib) - redefines proxy classes that implement or extend changed interfaces or classes.

Find detailed documentation of each plugin in the plugin project main README.md file.

### Runtime overhead
It depends on how many frameworks you use and which caches are disabled. Example measurements
for a large, real-world enterprise application based on Spring + Hibernate, run on Jetty.

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
            <groupId>org.hotswapagent</groupId>
            <artifactId>HotswapAgent</artifactId>
            <version>${project.version}</version>
            <scope>provided</scope>
        </dependency>
(Note that the JAR is not yet in central maven repository - you need to build it from source first).

See [ExamplePlugin](https://github.com/HotswapProjects/HotswapAgentExamples/blob/master/custom-plugin/src/main/java/org/hotswap/agent/example/plugin/ExamplePlugin.java)
(part of TestApplication) to go through a commented simple plugin. Read [agent readme](https://github.com/HotswapProjects/HotswapAgent/blob/master/README.md)
 to understand agent concepts. Check existing plugins source code for more examples.


Creating Release
================
Launch `run-tests.sh` script in the main directory. Currently, you have to setup JAVA_HOME location directory manually.
At least Java 7 and Java 8 with DCEVM should be checked before a release. All automatic tests are set to fail
the whole script in case of any single test failure.

Go to the directory representing repository root. In case DCEVM is named `dcevm`

    mvn release:prepare
    mvn release:perform

In case your DCEVM is named differently i.e. `server`

    mvn release:prepare -Darguments="-Ddcevm=server"
    mvn release:perform -Darguments="-Ddcevm=server"

Credits
=======
Hotswap agent:

* Jiri Bubnik - project coordinator, initial implementation
* Alexandros Papadakis - Maven Versioning, Weld, JSF, Hibernate3, RestEasy, WildFly plugins
* Erki Ehtla - Spring plugin, Proxy plugin
* Vladimir Dvorak - ELResolver, OsgiEquinox, Weld, Owb, Deltaspike, Jvm, Jdk, JBossModules, ClassInit, JSF
* Sergey Lysenko - Weld plugin
* Samuel Pelletier - WebObjects plugin
* Jan Tecl - web design
* Lukasz Warzecha - Log4j2 plugin
* Thomas Heigl - Wicket plugin
* AJ Banck - FreeMarker plugin
* Sinan Yumak - Mojarra, MyFaces plugins

DCEVM:

* Ivan Dubrov - current project coordinator, update to Java7+Java8, patches, build system (Gradle)
* Thomas Würthinger - initial implementation.
* Kerstin Breitender - contributor.
* Christoph Wimberger - contributor.
* Vladimir Dvorak - java9,java11 migration, contributor
* Jiri Bubnik - java9,java11 migration
