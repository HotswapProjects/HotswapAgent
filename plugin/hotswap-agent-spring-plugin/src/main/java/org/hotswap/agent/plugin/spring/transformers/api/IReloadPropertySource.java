package org.hotswap.agent.plugin.spring.transformers.api;

import org.hotswap.agent.plugin.spring.api.PropertySourceReload;

public interface IReloadPropertySource {

    void setReload(PropertySourceReload r);

    void reload();
}
