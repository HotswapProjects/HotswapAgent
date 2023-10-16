package org.hotswap.agent.plugin.spring.boot;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.boot.transformers.PropertySourceLoaderTransformer;
import org.hotswap.agent.plugin.spring.boot.transformers.PropertySourceTransformer;
import org.hotswap.agent.util.PluginManagerInvoker;

@Plugin(name = "SpringBoot", description = "Reload Spring Boot after properties/yaml changed.",
        testedVersions = {"All between 1.4.x - 2.7.x"}, expectedVersions = {"1.4.x+", "1.5.x+", "2.x"},
        supportClass = {PropertySourceLoaderTransformer.class,
                PropertySourceTransformer.class})
public class SpringBootPlugin {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(SpringBootPlugin.class);

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    public void init() throws ClassNotFoundException {
        LOGGER.info("Spring Boot plugin initialized");
        Class.forName("org.hotswap.agent.plugin.spring.boot.env.Boot1PropertiesPropertySourceReload", true, appClassLoader);
        Class.forName("org.hotswap.agent.plugin.spring.boot.env.Boot1YamlPropertySourceReload", true, appClassLoader);
        Class.forName("org.hotswap.agent.plugin.spring.boot.env.Boot2PropertiesPropertySourceReload", true, appClassLoader);
        Class.forName("org.hotswap.agent.plugin.spring.boot.env.Boot2YamlPropertySourceReload", true, appClassLoader);
    }

    public void init(String version) throws ClassNotFoundException {
        LOGGER.info("Spring Boot plugin initialized - Spring Boot core version '{}'", version);
        Class.forName("org.hotswap.agent.plugin.spring.boot.env.Boot1PropertiesPropertySourceReload", true, appClassLoader);
        Class.forName("org.hotswap.agent.plugin.spring.boot.env.Boot1YamlPropertySourceReload", true, appClassLoader);
        Class.forName("org.hotswap.agent.plugin.spring.boot.env.Boot2PropertiesPropertySourceReload", true, appClassLoader);
        Class.forName("org.hotswap.agent.plugin.spring.boot.env.Boot2YamlPropertySourceReload", true, appClassLoader);
    }

    @OnClassLoadEvent(classNameRegexp = "org.springframework.boot.SpringApplication")
    public static void register(ClassLoader appClassLoader, CtClass clazz, ClassPool classPool) throws NotFoundException, CannotCompileException {
        StringBuilder src = new StringBuilder("{");
        // init a spring plugin with every appclassloader
        src.append(PluginManagerInvoker.buildInitializePlugin(SpringBootPlugin.class));
        src.append(PluginManagerInvoker.buildCallPluginMethod(SpringBootPlugin.class, "init",
                "org.springframework.boot.SpringBootVersion.getVersion()", String.class.getName()));
        src.append("}");

        for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
            constructor.insertBeforeBody(src.toString());
        }
    }
}
