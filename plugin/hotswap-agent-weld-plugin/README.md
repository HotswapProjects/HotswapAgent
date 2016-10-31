[Weld/CDI](http://weld.cdi-spec.org/)
=====================================
Register a new created bean class into BeanManager. Reinject injection points after bean class redefinition.
Reload proxy factory after proxied class redefinition. Bean reloading strategy can be specified by
`weld.beanReloadingStrategy` in hotswap-agent.properties.

There are 4 possible values for this parameter:

    # Setup reloading strategy of bean INSTANCE(s) in Weld CONTEXT(s). While bean class is redefined by DCEVM, reloading of bean instances
    # can be customized by this parameter. Available values:
    #   - CLASS_CHANGE - reload bean instance on any class modification, plus reaload on changes specified in
    #     METHOD_FIELD_SIGNATURE_CHANGE and FIELD_SIGNATURE_CHANGE strategies
    #   - METHOD_FIELD_SIGNATURE_CHANGE - reload bean instance on any method/field change. Includes changes specified in
    #     strategy FIELD_SIGNATURE_CHANGE
    #   - FIELD_SIGNATURE_CHANGE - reload bean instance on any field signature change. Includes also field annotation changes
    #   - NEVER - never reload bean (default)
    # weld.beanReloadStrategy=NEVER

Reloading strategy  is powerfull mechanism how to control bean reloading according personal preferences. Most general strategy is `CLASS_CHANGE`.
This strategy ensures bean state consistency for each type of code change, unfortunately it leads to recreation of session beans and lost
of session subsequently. Less invasive strategies are `METHOD_SIGNATURE` and `FIELD_SIGNATURE_CHANGE`. These don't reload bean when method code
is changed but reload bean after method parameters are changed respectively class fields are changed. Less invasive strategy is strategy 'NEVER',
which never reloads beans. It can lead to session or application bean inconsistence. Strategy `NEVER` left the responsibility of bean reloading
to developer. Usually experienced developer knows the best when the bean modification leads to inconsistent state and are able to drive
bean reloading himself, therefore the default strategy is 'NEVER'.

#### Implementation notes:
Plugin initialization is done in `org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl` constructor in case if Weld is running under Wildfly or
in `org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive` in another cases. An instance of BeanDeploymentArchiveAgent is created for each
bean archive found and then it is registered in BdaAgentRegistry. ProxyFactory is patched to call ProxyClassLoadingDelegate methods when proxy class is
created. Each instance of ProxyFactory is registered in WeldPlugin in registeredProxiedBeans map. When proxied bean class is redefined than appropriate
proxy factory instance is found and that is forced to recreate proxy class.

# TODO:
If a new proxy class is created than weld creates common beans instead proxied beans.
