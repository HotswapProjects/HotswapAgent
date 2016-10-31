JSF plugin
==========
Supported Mojarra, MyFaces implementation. Clear resource bundle cache after any *.properties file is changed.

#### Implementation notes:
* Mojarra plugin initialization is triggered after com.sun.faces.config.ConfigManager.initialize() method in servlet classloader.
* MyFaces plugin is triggered in org.apache.myfaces.config.RuntimeConfig constructor

## TODO:
