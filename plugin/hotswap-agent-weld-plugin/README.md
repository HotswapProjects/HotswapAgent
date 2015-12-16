[Weld/CDI](http://weld.cdi-spec.org/)
=====================================
Add a new created class (bean) into BeanManager. Reinject injection points after bean class redefinition.
Reloads proxy factory after proxied class redefinition.

#### Implementation notes:
Plugin initialization is done in `org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl` constructor in case if weld is running under Wildfly or
in `org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive` in other cases. An instance of BeanDeploymentArchiveAgent is created for each
found bean archive and registered in BdaAgentRegistry.
ProxyFactory is patched to call ProxyClassLoadingDelegate methods when proxy class is created. Each instance of ProxyFactory is registered in WeldPlugin in registeredProxiedBeans map. When proxied bean class is redefined than appropriate proxy factory instance is found and that is forced to recreate proxy class.

# TODO:
If a new proxy class is created than weld creates common beans instead proxied beans.