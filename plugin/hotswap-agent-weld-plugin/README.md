[Weld/CDI](http://weld.cdi-spec.org/)
=====================================
Add a new created class (bean) into BeanManager. Reinject injection points after bean class redefinition.

#### Implementation notes:
Plugin initialization is done in `org.jboss.weld.bootstrap.WeldBootstrap` constructors. Then `org.jboss.weld.environment.deployment.WeldBeanDeploymentArchive`
class is patched to hook into registration method in `BeanDeploymentArchiveAgent`.

# TODO:
