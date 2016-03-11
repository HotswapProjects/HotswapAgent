[Seam framework](http://seamframework.org/)
===========================================
Clears `java.beans.Introspector` cache and `org.jboss.el.util.ReferenceCache` on any class change.

#### Implementation notes:
* Plugin initialization is triggered before org.jboss.seam.init.Initialization.init() method in servlet classloader.
