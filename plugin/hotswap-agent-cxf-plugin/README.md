[CXF](http://cxf.apache.org/)
=============================

[CxfJAXRSPlugin](http://cxf.apache.org/docs/jax-rs.html)
=======================================================
CxfJAXRSPlugin redefine resource classes (services) on resource class modification.

This plugin does not handle javax.ws.rs.ext.* annotations like @Provider

#### Implementation notes:
ClassResourceInfo instance is proxied using javassist delegating proxy. When service class is modified then delegating instance of
ClassResource info is recreated using original creational parameters from first definition. Injection points in service innstance
are re-injected in following enviroments:
* CDI - Weld
* CDI - OWB

