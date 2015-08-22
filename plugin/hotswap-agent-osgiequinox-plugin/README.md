OsgiEquinox / Eclipse plugin
============================
OsgiEquinox / Eclipse plugin provides hotswap support for Eclipse plugin or platform development
(Do not confuse it with common development in Eclipse!).


Configuration
-------------
Following options should be setup in eclipse.ini for debugee Eclipse instance:

     # use application classloader for the framework
    -Dosgi.frameworkParentClassloader=app
     # development classpath that is added to each plugin classpath
    -Dosgi.dev=[extra_classpath]
     # use dcevm as JVM
    -XXaltjvm=dcevm
     # enable hotswapagent
    -javaagent:PATH_TO_AGENT/hotswap-agent.jar
     # enable remote debugging on port 8000
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000

extra_classpath points to directory with compiled classes. When a new class is compiled it is sent by remote debugger to HotswapAgent. HotswapAgent
stores this file into extra_classpath directory.

It is also necessary to setup following hotswap-agent.properties:

    extraClasspath=[extra_classpath]
    osgiEquinox.debugMode=true

then connect the IDE debugger (eclipse, netbeans or idea) to port 8000 and happy hotswapping!

#### Implementation notes:

## TODO:
