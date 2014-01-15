Hotswap Agent
=============

Java unlimited runtime class and resource redefinition. The main purpose of this project is to avoid
infamous change->*restart*->check development lifecycle. What about Save&Reload (similar to PHP)?
This project is still in a beta version.

# Easy to start
Download and install DCEVM Java patch + agent jar and launch your application server with -javaagent
option to get basic setup.

    -XXaltjvm=dcevm -javaagent:HotswapAgent.jar

Optionally add hotswap-agent.properties to your application to configure plugins and agent behaviour.

# Plugins
Each application framework (Spring, Hibernate, Logback, ...) needs special reloading mechanism to keep
up-to-date after class redefinition (e.g. Hibernate configuration reload after new entity class is introduced).
Hotswap agent works as an plugin system and comes preconfigured with all major framework plugins. It is easy
to write your custom plugin even as part of your application.

# IDE support
None needed :) Really, all changes are transparent and all you need to do is to download patch+agent and
setup your application server. Because we use standard java hotswap behaviour, your IDE will work as expected.
However, we work on IDE plugins to help with download & configuration.

DCEVM
-----
Hotswap agent does the work of reloading resources and framework configuration (Spring, Hibernate, ...),
but it depends on standard Java hotswap mechanism to actually reload classes. Standard Java hotswap allows
only method body change , which makes it practically unusable. DCEVM is a JRE patch witch allows almost any
structural class change on hotswap (with an exception of a supertype change). Although hotswap agent works
even with standard java, we recommend to use DCEVM (and all tutorials use DCEVM as target JVM).

Hotswap agent
-------------





Plugins
-------


Configuration
-------------
The basic configuration is there to allow class reloading


Features
-------------
# Zero downtime redeploy
You can configure the agent to automatically reload changed class file automatically (without IDE). This
may be used to upload changed classes even on a production system without restart (note, that the agent is
not stable enough yet, use at your own risk).


Credits
-------------
Jiri Bubnik - Hotswap agent author
- DCEVM author
- DCEVM update to Java7, patches, build system (Gradle)
Jan Tecl - Hotswap agent web design
