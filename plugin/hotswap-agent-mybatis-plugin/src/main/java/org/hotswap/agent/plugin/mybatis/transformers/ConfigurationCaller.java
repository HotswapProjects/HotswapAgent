package org.hotswap.agent.plugin.mybatis.transformers;

import org.apache.ibatis.session.Configuration;
import org.hotswap.agent.util.ReflectionHelper;

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
}
