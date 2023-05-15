[Thymeleaf](https://www.thymeleaf.org/) plugin
=================
Clear the template cache when a template is modified. It's verified on version 3.0.15 only.

#### Implementation notes

* Plugin initialization is triggered when org.thymeleaf.engine.TemplateManager is loaded.
* Patch org.thymeleaf.engine.TemplateManager#parseAndProcess by adding the following code snippet:
    ```java
    public void parseAndProcess(
            final TemplateSpec templateSpec,
            final IContext context,
            final Writer writer) {
        clearCachesFor(templateSpec.getTemplate());
        ...
   }
   ```