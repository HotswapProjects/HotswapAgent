Hibernate plugin
================
Reload Hibernate EntityManagerFactory / SessionFactory after entity class definition/change.

The plugin hooks for initialization into `org.hibernate.ejb.HibernatePersistence` (for EJB 3.0) or
`org.hibernate.cfg.Configuration` (for plain Hibernate) to wrap `javax.persistence.EntityManagerFactory` or
`org.hibernate.SessionFactory` with a proxy. All clients then obtain reference for the proxy only.

Hibernte plugin listens for a change and hotswap on all classes on classpath. If the class contains
`@javax.persistence.Entity` annotation, whole Hibernate configuration is reloaded and target factory is
swapped in the proxy.

New `EnityManager`/`SessionFactory` instance is than obtained on next `EnityManager.createEntityManager()` call.
`EntityManager` created before reload remains unchanged.


#### Implementation notes:
`HibernateTransformers` registers static transformer for main Hibernate configuration class
`org.hibernate.ejb.HibernatePersistence`. The methods `createEntityManagerFactory /
createContainerEntityManagerFactory` are wrapped with a call to
`HibernatePersistenceHelper.createContainerEntityManagerFactoryProxy`, which creates a proxy and
registers the proxy in static context to be accessible for the reloading.

Plugin instance `HibernatePlugin.entityReload() / HibernatePlugin.newEntity()` then listen
for hotswap / new class file and schedules an `HibernateRefreshCommands.reloadEntityManagerFactory()`
command to run in the application classloader. This translates directly to invocation of
`EntityManagerFactoryProxy.refreshProxiedFactories()` which in turn calls `refreshProxiedFactory()` for
each registered factory. The refresh is similar to standard Hibernate new configuration loading mechanism
in `org.hibernate.ejb.HibernatePersistence`.


## TODO:
* Check for all Hibernate annotations (not just Entity)
* Listen only on a package (not whole classpath) - probably hook somewhere in in the hibernate processing,
  because persistence.xml or .hbm.xml can be overridden by dynamic configuration (for example by Spring)
* `HibernatePlugin.entityReload` - check if the class is an Entity can be resolved by Hibernate, not just
  the annotation.
* Selective reload (because full reload is fast and very easy, this has low priority)