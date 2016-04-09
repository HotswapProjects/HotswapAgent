[ResteasyRegistry plugin](http://)
===========================================

RESTeasy plugin which cleanups and registers class redefinitions in the RESTeasy ResourceMethodRegistry

Should not be used together with Resteasy plugin

This plugin does not handle javax.ws.rs.ext.* annotations like @Provider

#### Implementation notes:

Resteasy registers methods based the class Path annotation. Since the redefined class
may be in a different Path ( or some of its methods) we need to iterate over known
bounded methods, reconstruct the class/method path and remove each of the currently registered methods.

Since the class/method path declarations may have not changed, we use the registry removeRegistrations method.