Proxy plugin
=============
Redefine sythetic classes - proxies. Proxy classes are used by many frameworks. Currently it supports proxies created with Java reflection
(com.sun.proxy.Proxy) and the Cglib library.

#### Implementation notes:
java.lang.reflect Proxy - new proxy byte code is generated after proxied class redefinition is finished by JVM. So proxy class redefinition is deferred
and is implemented by HotswapAgent command.

Cglib proxy replacement is two-step process. For Cglib proxies you may recieve exceptions when the classes are acccessed before the second step has finished.
Cglib proxy replacement does not use package names to detect proxy Class definition generators. This done so to support frameworks that repackage libraries
(for example the Spring Framework repackages Cglib). The plugin just checks the interface name and method name to detect a Cglib proxy Class definition generator.
This may cause problems if you have a interface named GeneratorStrategy that also has a method named generate.

# TODO:
