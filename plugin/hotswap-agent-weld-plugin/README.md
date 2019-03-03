[Weld/CDI](http://weld.cdi-spec.org/)
=====================================
Reinject injection points after bean redefinition. Define and register a new bean in BeanManager on new bean definition.
Redefine proxy class if proxied class is redefined. Appropriate redefinition can be specified in `hotswap-agent.properties` file.
There are 2 approaches what to do after bean class redefinition:

* reinject injection points in existing bean instances - old bean instances **survives**
* reload existing bean instances in contexts - old bean instances are **lost**

Weld plugin uses reinjection by default, but it is not desired in some cases. Therefore it is possible to specify precise reloading strategy
in `hotswap-agent.properties config` file using parameter `weld.beanReloadStrategy` using following values:

* NEVER - (default strategy) reinject existing bean instances after bean class redefinition, never reload contexts
* FIELD_SIGNATURE_CHANGE - reload bean instances after any field change including annotation of fields
* METHOD_FIELD_SIGNATURE_CHANGE reload bean instances after any method/field change including all modifications specified for FIELD_SIGNATURE_CHANGE
* CLASS_CHANGE - reload bean instances after any class change and any modification from previous strategies.

#### Implementation notes:
Plugin initialization is done in `org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl` constructor in case if Weld is running under Wildfly or
in `org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive` in other cases. An instance of BeanDeploymentArchiveAgent is created for each
bean archive found and then it is registered in BdaAgentRegistry. ProxyFactory is patched to call ProxyClassLoadingDelegate methods when proxy class is
created. Each instance of ProxyFactory is registered in WeldPlugin in registeredProxiedBeans map. When proxied bean class is redefined than appropriate
proxy factory instance is found and is forced to recreate proxy class.

# TODO:
If a new proxy class is created than weld creates common beans instead proxied beans.
