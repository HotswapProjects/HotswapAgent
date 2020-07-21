Mojarra plugin
==================
- Clear resource bundle cache after any *.properties file is changed.
- Reinject @ViewScoped beans, reinject Omnifaces @ViewScoped beans.
- Reload `ManagedBean` annotated beans on class redefinition / change.
- Register `ManagedBean` annotated beans on class definition.

#### Implementation notes:
Plugin initialization is triggered after com.sun.faces.config.ConfigManager.initialize() method in servlet classloader.

The plugin listens defined/changed `ManageBean` classes and 
adds them to the dirty bean list. The dirty beans will be reloaded
on the next call to the servlet.

`BeanManagerTransformer` is used to add reloading features to 
`BeanManager`. It basically adds a list to the class to hold the dirty beans 
and it also adds helper methods to process the dirty beans. 

`LifecycleImplTransformer` is used to patch `LifecycleImpl.execute()` method.
This method is patched to call process dirty beans after the execute method. 
