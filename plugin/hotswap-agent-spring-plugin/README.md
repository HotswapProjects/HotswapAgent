Spring plugin
=============
Reload Spring configuration after class definition/change.

The plugin hooks for initialization into `DefaultListableBeanFactory` which is the default bean factory for
all applicationContexts. This plugin should work for you if you use a standard Spring setup.

Currently component scan and annotation config is supported (as it is the most common configuration).
and XML-based bean definition is available (basePackagePrefix attribute is needed to improve performance).
For more complex configuration reload any help is warmly welcomed :-).

The parameter `-DSpringReloadDelayMillis=` can be used to specify the delay time after redefinition. The default value 
is 1600, but according to tests, a value of approximately 500 is reliable.

> Note
>
> Instances of non-singleton bean with no default constructor created before class file change won't be rewired with new props. It just cost too much pain too implement that with so little in return.
>
>  
>
> A bean defined by xml must have a id/name equals className with lowercase first letter or instances created before won't be rewired with new props (Updating corrsponding xml can trigger rewiring. For me, it's weird to define a bean in xml which have @Autowire props/methods).



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

---

For xml defined bean:

Hooked XmlBeanDefinitionReader.loadBeanDefinitions(URL url) method to get it's args, and use that method to loadBeanDefinition when xml changed.

If you are running server using IDE, just change the xml and save it.
If the server is running standalone, you need to change the xml under server's webapp path with autoHotswap on, because there is actually no hotswap for xml

# TODO:
* ... a lot to do ...