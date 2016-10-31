[JBoss modules](http://)
========================================
Add extraClassPath from hotswap-agent.properties to org.jboss.modules.ModuleClassLoader. Therefore
extraClassPath can be used for all enviroments based on this jsboss's class loader (WildFly).

Configuration
-------------
Setup extraClasspath property in hotswap-agent.properties.

    #
    # Add this classpath prior to application classpath.
    # This may be useful for example in multi module maven project, where you do not need to build JAR file
    # after each change.
    #
    # Note that there must be a plugin that will provide actual replacement such as JettyPlugin for Jetty servlet container.
    extraClasspath=

#### Implementation notes:
Plugin is initialized when `loadModule` of `org.jboss.modules.ModuleLoader'` is called. Plugin is unload when
`unloadModule` is called.

TODO
----
