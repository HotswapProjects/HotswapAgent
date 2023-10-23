package org.hotswap.agent.plugin.spring.transformers.api;

import org.springframework.util.StringValueResolver;

import java.util.List;

public interface IPlaceholderConfigurerSupport {

    List<StringValueResolver> valueResolvers();
}
