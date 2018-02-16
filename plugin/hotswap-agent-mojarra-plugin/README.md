Mojarra plugin
==================
Clear resource bundle cache after any *.properties file is changed.

#### Implementation notes:
Plugin initialization is triggered after com.sun.faces.config.ConfigManager.initialize() method in servlet classloader.