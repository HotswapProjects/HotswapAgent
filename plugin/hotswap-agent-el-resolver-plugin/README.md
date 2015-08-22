EL-Resolver plugin
==================
Clear `javax.el.BeanELResolver` cache after any class is redefined.
Following implementations supported :
* JuelEL,
* ApacheCommons EL
* JBossEL.

#### Implementation notes:
Plugin initialization is triggered after at the end of `javax.el.BeanELResolver` constructor.
Method `__resetCache` is inserted into `javax.el.BeanELResolver` class. This method is called
by `org.hotswap.agent.plugin.elresolver.PurgeBeanELResolverCacheCommand`

## TODO:
* Support for MyFaces
* Support for another EL implementations