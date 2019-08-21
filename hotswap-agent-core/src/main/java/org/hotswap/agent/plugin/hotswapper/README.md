Hotswapper
===========
Watch for any class file change and reload (hotswap) it on the fly.
 
Although it is usually more convenient to use your IDE debugger for hotswap during development, this
can be utilized to reload classes even on production server! Be careful and test it thoroughly before use :-)

Plugin configuration
--------------------
Just specify

    # Watch for changed class files on watchResources path and reload class definition in the running application.
    #
    # Usually you will launch debugging session from your IDE and use standard hotswap feature.
    # This property is useful if you do not want to use debugging session for some reason or
    # if you want to enable hotswap at runtime environment.
    #
    # Internally this uses java Instrumentation API to reload class bytecode. If you need to use JPDA API instead,
    # specify autoHotswap.port with JPDA port.
    autoHotswap=false

You need to start java with JPDA enabled. For example:

    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000 --XXaltjvm=dcevm -javaagent:HotswapAgent.jar MainClass

will start the application with a debugger api enabled at localhost:8000. The other parameters are related to
Hotswap agent setup itself. With this setup, you can use your IDE to attach debugger to localhost:8000. Btw. this
is the way how your IDE attaches normally to the debugging process.

To configure the plugin to attach to the process, you need to enable it in hotswap-agent.properties:

     # Create Java Platform Debugger Architecture (JPDA) connection on autoHotswap.port, watch for changed class files
     # and do the hotswap (reload) in background.
     #
     # Normally you will launch debugging session from your IDE of use hotswap feature.
     # This property is useful if you do not want to use debugging session for some reason or
     # to enable hotswap at runtime environment.
     #
     # You need to specify JPDA port at startup
     # <pre>java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000</pre>
     autoHotswap=true

     # Port on which you started JPDA session (address parameter - see autoHotswap property description)
     # port 8000 is the default
     autoHotswap.port=8000

If you enable the `autoHotswap=true` in your application, all class files that are on the classpath of the application
(same classloader as hotswap-agent.properties) will be watched for changes and reloaded in the JVM by hotswap command.

Note that you can have multiple applications and hotswap-agent.properties for an application server and you need
to enable autoHotswap for each application (classloader) separately.

