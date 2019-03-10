[CXF](http://cxf.apache.org/)
=============================

[CxfJAXRSPlugin](http://cxf.apache.org/docs/jax-rs.html)
=======================================================
CxfJAXRSPlugin redefines resource classes (services) on resource class modification and handles JAXB classes redefinition by clearing JAXB contexts.

This plugin does not handle javax.ws.rs.ext.* annotations like @Provider

#### Implementation notes:
ClassResourceInfo instance is proxied using javassist delegating proxy. When service class is modified then delegating instance of ClassResource info is recreated using original creational parameters from first definition. Injection points in service instance are re-injected in following enviroments:
* CDI - Weld
* CDI - OWB
* Spring - default singleton beans reloading using SpringPlugin

