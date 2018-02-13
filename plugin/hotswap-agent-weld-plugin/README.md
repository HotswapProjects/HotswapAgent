[Weld/CDI](http://weld.cdi-spec.org/)
=====================================
Reinject injection points after bean redefinition. Define and register a new bean into running BeanManager if a new bean class is defined.
Redefine proxy class if proxied class is redefined. Appropriate redefinition can be specified in `hotswap-agent.properties` file. Generally
there are 2 approaches what to do after bean class redefinition:

* reinject injection points in existing bean instances - bean state **survives**
* reload existing bean instances in contexts - the bean state **is lost**

Weld plugin uses reinjection by default, but it could be not desired in all cases. Therefore precise reloading strategy can be specified
in `hotswap-agent.properties config` file using parameter `weld.beanReloadStrategy`. Following values are allowed:

* NEVER - (default strategy) reinject existing bean instances after bean class redefinition and never reload contexts
* FIELD_SIGNATURE_CHANGE - reload bean instance after any field is changed including annotation of fields as well.
* METHOD_FIELD_SIGNATURE_CHANGE reload bean instance after any method/field is changed including all modification specified for FIELD_SIGNATURE_CHANGE
* CLASS_CHANGE - reload bean after any class change and any modification in previous strategies.

#### Implementation notes:
Plugin initialization is done in `org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl` constructor in case if Weld is running under Wildfly or
in `org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive` in another cases. An instance of BeanDeploymentArchiveAgent is created for each
bean archive found and then it is registered in BdaAgentRegistry. ProxyFactory is patched to call ProxyClassLoadingDelegate methods when proxy class is
created. Each instance of ProxyFactory is registered in WeldPlugin in registeredProxiedBeans map. When proxied bean class is redefined than appropriate
proxy factory instance is found and that is forced to recreate proxy class.

# TODO:
If a new proxy class is created than weld creates common beans instead proxied beans.
