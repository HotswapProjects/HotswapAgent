[Jersey 2](https://jersey.java.net/)
====================================
Jersey2 reloading support for all root resource classes (classes annotated by `javax.ws.rs.Path`) and provider classes.

#### Implementation notes:
Hook into `org.glassfish.jersey.servlet.WebConfig` call. Register all *scanClasses* and *registeredClasses*
from `org.glassfish.jersey.server.ResourceConfig` and trigger reloading of jersey container on any root resource class change.
Fixes issue with opened scanned files on the classpath https://java.net/jira/browse/JERSEY-1936.
