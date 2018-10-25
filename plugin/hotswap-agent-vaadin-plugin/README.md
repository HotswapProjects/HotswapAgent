[Vaadin Platform](https://vaadin.com/)
====================================

Vaadin is a development platform for web applications that prioritizes ease of development and uncompromised end user experience.

To create a vaadin project, go to https://vaadin.com/start

= Features
* Register changes in `@Route` to the router when a class is modified (add new views on the fly)
* Changes to a template model are live after a browser refresh
* All internal metadata caches are cleared whenever a class is changed

Known issues:
* Removing a class with `@Route` does not remove the mapping

