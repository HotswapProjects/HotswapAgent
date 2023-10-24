Spring plugin
=============
Reload Spring Boot configuration after class definition/change.

The plugin hooks for initialization into `org.springframework.boot.SpringApplication` which is the entry of Spring Boot Application.

The plugin based on Spring Plugin, it depends on Spring Dependency. The spring boot have some features, now we just support the reload of the configuration.

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