Spring plugin
=============
Reload Spring configuration after class definition/change.

The plugin hooks for initialization into `DefaultListableBeanFactory` which is the default bean factory for
all applicationContexts. This plugin should work for you if you use a standard Spring setup.

Currently only component scan and annotation config is supported (as it is the most common configuration).
At least XML-based bean definition will be available in a near future as well. For more complex configuration
reload any help is warmly welcomed :-).

Component scan
--------------
Plugin supports reloading of all components registered by component scan
([Spring classpath scanning documentation](http://docs.spring.io/spring/docs/4.0.x/spring-framework-reference/html/beans.html#beans-classpath-scanning)).
For example:

    <context:component-scan base-package="org.example"/>

Spring will scan package org.example at initialization and register all beans. HotswapAgent plugin will register
events on the base package as well to catch any class reload or new class definition. After you reload a class or
define a new class, the class is processed by Spring container as if it was discovered by standard scan process.
If the bean was already registered in Spring bean registry, it is unregistered and all appropriate caches are reset.

#### Implementation notes:
The plugin is initialized in `DefaultListableBeanFactory` constructor. Than `ClassPathBeanDefinitionScanner` is patched
to hook into component scan process (actually
`org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider.findCandidateComponents(String basePackage))`
is enhanced to register hotswap reload and file creation events on basePackage path. After the event is fired,
a method similar to `org.springframework.context.annotation.ClassPathBeanDefinitionScanner.doScan()` is invoked. There
are two main differences - it scans only a single file and unregister bean definition from bean registry if required.


# TODO:
* Add reload of beans defined in a XML file
* ... a lot to do ...