[Jersey 1](https://jersey.java.net/)
====================================
Jersey1 reloading support for all root resource classes (classes annotated by `javax.ws.rs.Path`) and provider classes.

#### Implementation notes:
Hook into `com.sun.jersey.spi.container.servlet.ServletContainer` call. Register all *scanClasses* and *registeredClasses*
from `com.sun.jersey.api.core.ResourceConfig` and trigger reloading of jersey container on any root resource class change.
