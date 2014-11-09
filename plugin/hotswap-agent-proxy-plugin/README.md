Proxy plugin
=============
Redefines proxy classes that implement or extend changed interfaces or classes. Currently it supports proxies created with Java reflection and the Cglib library.

#### Implementation notes:
java.lang.reflect.Proxy proxy replacement is a one-step process, Cglib on the other hand a two-step one. 
So for Cglib proxies you may recieve exceptions when the classes are acccessed before the second step has finished.
Cglib proxy replacement does not use package names to detect proxy Class definition generators. This done so to support frameworks that repackage libraries (for example the Spring Framework repackages Cglib). 
The plugin just checks the interface name and method name to detect a Cglib proxy Class definition generator. This may cause problems if you have a interface named GeneratorStrategy that also has a method named generate.

# TODO:
