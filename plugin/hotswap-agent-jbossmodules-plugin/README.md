[JBoss modules](http://)
========================================

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

StandardContext (parsed web.xml configuration):
* watchedResources

ApplicationContext:
* getResource(String path) - override webapp directory location
* ClassLoader getClassLoader()  -> ClassLoader result = context.getLoader().getClassLoader();

TODO
----
* add 
