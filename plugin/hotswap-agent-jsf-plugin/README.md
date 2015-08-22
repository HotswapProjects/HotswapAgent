JSF plugin
==========
Clear resource bundle cache after any .properties file change.

#### Implementation notes:
* Plugin initialization is triggered after com.sun.faces.config.ConfigManager.initialize() method in servlet classloader.
* Mojarra supported

## TODO:
* Support for MyFaces
