Myfaces plugin
==================
- Clear resource bundle cache after any *.properties file is changed.
- Reinject @ViewScoped beans.
- Reload `ManagedBean` annotated beans on class redefinition / change.
- Register `ManagedBean` annotated beans on class definition.

#### Implementation notes:
MyFaces plugin is triggered in org.apache.myfaces.config.RuntimeConfig constructor

The plugin listens defined/changed `ManageBean` classes and 
adds them to the dirty bean list. The dirty beans will be reloaded
on the next call to the servlet.

`ManagedBeanResolverTransformer` is used to add reloading features to 
`ManagedBeanResolver`. It basically adds a list to the class to hold the dirty beans 
and it also adds helper methods to process the dirty beans. 

`LifecycleImplTransformer` is used to patch `LifecycleImpl.execute()` method.
This method is patched to call process dirty beans after the execute method. 
