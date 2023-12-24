Spring Boot plugin
==================

Dynamic reloading of Spring Boot configuration files in real-time. Directly leverages Spring's environment and 
context mechanisms to ensure seamless integration.

It works with Spring Boot 1.5.x and 2.0.x.

Applicability
--------------
The plugin is specifically designed for Spring Boot applications where starters are responsible for managing their own 
configuration properties and corresponding beans. It does not enforce a global refresh of all beans but rather delegates 
to each starter to listen for configuration changes and make decisions on which beans to refresh. If not for this way, 
we may need to destroy and recreate all spring boot beans which is not a good idea.

Configuration Reload
--------------
The plugin supports reloading of Spring Boot configuration. For example:
```
    ####hotswap-demo.properties
    properties.l10.l1=hotswap-demo

    @Configuration
    @PropertySource("classpath:hotswap-demo.properties")
    @EnableConfigurationProperties(value = {Test10Properties.class})
    public class ApplicationConfiguration {
        @Value("${application.name}")
        private String applicationName;
        ...
    }

    @ConfigurationProperties(prefix = "properties.l10")
    public class Test10Properties {
        private String l1;
        ...
    }
    
    @Component
    public class Test50Service {
        @Value("${properties.l10.l1}")
        private String l1;
        ...
    }

```
It will reload the configuration when the `hotswap-demo.properties` file changed and send an event to notify the configuration changed.

#### Refresh the configuration

If you change the value of `properties.l10.l1` in `hotswap-demo.properties` file or `application.yaml/application.properties` file, 
* the value in any beans which use `@Value` will be changed. (destroy and recreate the bean)
* the value in any beans which use `@ConfigurationProperties` will be changed. (destroy and recreate the bean)

#### Notify the configuration change 

After any value of PropertySource changed, it will send an event that provided by Spring Plugin. 
You can implement one listener to receive the event. It will be used by any Hotswap plugin of Spring Boot Starter.

```
public class PropertiesChangeMockListener implements SpringListener<SpringEvent<?>> {

    public boolean isFilterBeanFactory() {
        return false;
    }

    @Override
    public DefaultListableBeanFactory beanFactory() {
        return null;
    }

    @Override
    public void onEvent(SpringEvent<?> event) {
        if (event instanceof PropertiesChangeEvent) {
            xxxxx
        }
    }
```

# TODO:
* ... a lot to do ...