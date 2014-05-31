Watch resources plugin
======================
Support for watchResources configuration property.

This plugin creates special WatchResourcesClassLoader witch returns only modified resources on watchResources
path. It then modifies application classloader to look for resources first in WatchResourcesClassLoader and
only if the resource is not found, standard execution proceeds.

Works for any java.net.URLClassLoader which delegates to URLClassPath property to findResource() (typical
scenario). For other classloader types a warning is logged to a console:
 
        *** watchResources configuration property will not be handled on JVM level ***
        
however, other plugins may be in place to fulfill this job. 