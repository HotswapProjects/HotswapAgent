package org.hotswap.agent.plugin.mybatis.transformers;

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.util.ReflectionHelper;

import java.util.Map;

public class ConfigurationCaller {
    /**
     * Sets the value of the $$ha$inReload field in the given MyBatis configuration.
     * This field is used to determine if the configuration is in reload status.
     *
     * @param configuration The MyBatis configuration object to be modified.
     * @param val           The value to set for the $$ha$inReload field. If true,
     *                      the configuration will be marked as being in reload status.
     */
    public static void setInReload(Configuration configuration, boolean val) {
        // Use ReflectionHelper to set the value of the $$ha$inReload field in the configuration object.
        ReflectionHelper.set(configuration, MyBatisTransformers.IN_RELOAD_FIELD, val);
    }

    public static boolean isMybatisObj(Configuration configuration, Class<?> clazz) {
        // is Mybatis result obj
        Object resultMapsObj = ReflectionHelper.get(configuration, "resultMaps");
        if (resultMapsObj != null) {
            Map resultMaps = (Map) resultMapsObj;
            for (Object resultMapObj : resultMaps.values()) {
                if (!(resultMapObj instanceof ResultMap)) {
                    continue;
                }
                ResultMap resultMap = (ResultMap) resultMapObj;
                if (clazz.getName().equals(resultMap.getType().getName())) {
                    return true;
                }
            }
        }

        // is Mybatis parameter obj
        Object parameterMapsObj = ReflectionHelper.get(configuration, "parameterMaps");
        if (parameterMapsObj != null) {
            Map parameterMaps = (Map) parameterMapsObj;
            for (Object parameterMapObj : parameterMaps.values()) {
                if (!(parameterMapObj instanceof ParameterMap)) {
                    continue;
                }
                ParameterMap parameterMap = (ParameterMap) parameterMapObj;
                if (clazz.getName().equals(parameterMap.getType().getName())) {
                    return true;
                }
            }
        }

        // is Mybatis mapper obj
        Object mapperRegistryObj = ReflectionHelper.get(configuration, "mapperRegistry");
        if (mapperRegistryObj != null) {
            MapperRegistry mapperRegistry = (MapperRegistry) mapperRegistryObj;
            for (Class<?> mapper : mapperRegistry.getMappers()) {
                if (clazz.getName().equals(mapper.getName())) {
                    return true;
                }
            }
        }

        return false;
    }
}
