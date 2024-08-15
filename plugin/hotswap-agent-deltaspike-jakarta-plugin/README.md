DeltaspikeJakarta plugin
========================
Process following actions on class change or class definition
* Reinject @WindowScoped, @GroupedConversationScoped beans on class redefinition
* Redefine repository components on repository class redefinition
* Reload Messages + JsfMessages on resource bundle file redefinition
* Reload ViewConfig on view config class redefinition
* Redefine DeltaSpike proxy class on base class redefinition
* Redefine Partial Beans

#### Implementation notes:
Plugin registers itselfs in following class instances initialization:
* org.apache.deltaspike.partialbean.impl.PartialBeanBindingExtension
* org.apache.deltaspike.data.impl.meta.RepositoryComponent
* org.apache.deltaspike.jsf.impl.config.view.ViewConfigExtension

## TODO:
* possibly support for other modules
