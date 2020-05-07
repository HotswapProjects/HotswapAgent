[Vaadin Platform](https://vaadin.com/)
====================================

Vaadin is a development platform for web applications that prioritizes ease of development and uncompromised end user experience.

To create a Vaadin project, go to https://vaadin.com/start.

Features
--------
* Register changes in `@Route` to the router when a class is modified (add new views on the fly)
* Changes to a template model are live after a browser refresh
* All internal metadata caches are cleared whenever a class is changed
* Automatic browser refresh when Java classes are reloaded  

Configuration
-------------
The following properties may be added to `hotswap-agent.properties`:
* `vaadin.liveReloadQuietTime`: the number of milliseconds to wait for the IDE to finish
  Java class compilation before triggering browser reload (default is `1000`).

Known issues
------------
* The plugin is currently compatible with Vaadin versions 13 or older 
* Removing a class with `@Route` does not remove the mapping

