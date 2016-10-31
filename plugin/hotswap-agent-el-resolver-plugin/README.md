EL-Resolver plugin
==================
Clear `javax.el.BeanELResolver` cache after any class is redefined.
Following implementations are supported :
* JuelEL
* ApacheEL (tomcat-el-api)
* JBossEL

#### Implementation notes:
Plugin resolves appropriate implementaition of BeanELResolver according specic methods that differs
in each supported implementation. Plugin initialization is triggered at the end of `javax.el.BeanELResolver`
constructor. Method `__resetCache` is inserted into `javax.el.BeanELResolver` class. This method is called
by `org.hotswap.agent.plugin.elresolver.PurgeBeanELResolverCacheCommand` on any class redefinition.

## TODO:
* Support for another EL implementations
