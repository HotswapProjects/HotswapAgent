package org.hotswap.agent.plugin.spring.boot.utils;

import org.hotswap.agent.util.ReflectionHelper;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;

import java.util.Arrays;

public class BinderUtils {
    private static boolean existBinder = false;
    private static boolean existRelaxedDataBinder = false;

    static {
        try {
            Class.forName("org.springframework.boot.context.properties.bind.Binder");
            existBinder = true;
        } catch (ClassNotFoundException e) {
        }

        try {
            Class.forName("org.springframework.boot.bind.RelaxedDataBinder");
            existRelaxedDataBinder = true;
        } catch (ClassNotFoundException e) {
        }
    }

    public static <T, S> T bind(ConfigurableEnvironment environment, PropertySource<S> propertySource, String prefix, Class<T> type) {
        if (existRelaxedDataBinder) {
            return relaxedDataBinderBind(propertySource, prefix, type);
        } else if (existBinder) {
            return binderBind(environment, propertySource, prefix, type);
        } else {
            throw new IllegalStateException(
                    "Can not find class org.springframework.boot.context.properties.bind.Binder or org.springframework.boot.bind.RelaxedDataBinder");
        }

    }

    private static <T, S> T binderBind(ConfigurableEnvironment environment, PropertySource<S> propertySource,
                                       String prefix, Class<T> type) {
        PropertySourcesPlaceholdersResolver placeholdersResolver = new PropertySourcesPlaceholdersResolver(environment);
        Iterable<ConfigurationPropertySource> iterator = Arrays.asList(ConfigurationPropertySource.from(propertySource));
        Binder binder = new Binder(iterator, placeholdersResolver, null, null, null);
        Bindable bindable = Bindable.of(type);
        BindResult bindResult = binder.bind(prefix, bindable);
        try {
            return (T) bindResult.orElse(type.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T, S> T relaxedDataBinderBind(PropertySource<S> propertySource, String prefix, Class<T> type) {
        T instance;
        try {
            instance = type.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(propertySource);
        Object propertySourcesPropertyValues = null;
        try {
            propertySourcesPropertyValues = ReflectionHelper.invokeConstructor("org.springframework.boot.bind.PropertySourcesPropertyValues", type.getClassLoader(),
                    new Class[]{PropertySources.class}, propertySources);
            Object binder = ReflectionHelper.invokeConstructor("org.springframework.boot.bind.RelaxedDataBinder", type.getClassLoader(),
                    new Class[]{Object.class, String.class}, instance, prefix);
            ReflectionHelper.invoke(binder, Class.forName("org.springframework.boot.bind.RelaxedDataBinder"),
                    "bind",
                    new Class[]{Class.forName("org.springframework.beans.PropertyValues")},
                    propertySourcesPropertyValues);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
