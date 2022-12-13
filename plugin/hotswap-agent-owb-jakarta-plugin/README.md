[Open Web Beans Jakarta/CDI](http://openwebbeans.apache.org/)
=====================================
Reinject injection points after bean redefinition. Define and register a new bean in BeanManager on new bean definition.
Redefine proxy class if proxied class is redefined. Appropriate redefinition can be specified in `hotswap-agent.properties` file.
There are 2 approaches what to do after bean class redefinition:

* reinject injection points in existing bean instances - old bean instances **survive**
* reload existing bean instances in contexts - old bean instances are **lost**

OWB plugin uses reinjection by default, but it is not desired in some cases. Therefore it is possible to specify reloading strategy
in `hotswap-agent.properties config`: file using parameter `owb.beanReloadStrategy` with following values:

* NEVER - (default strategy) reinject existing bean instances after bean class redefinition, never reload contexts
* FIELD_SIGNATURE_CHANGE - reload bean instances after any field change including annotation of fields.
* METHOD_FIELD_SIGNATURE_CHANGE reload bean instances after any method/field change including all modification specified for FIELD_SIGNATURE_CHANGE
* CLASS_CHANGE - reload bean instances after any class change and any modification from previous strategies.

#### Implementation notes:

# TODO:
