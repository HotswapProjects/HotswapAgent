package org.hotswap.agent.plugin.owb_jakarta.testBeans;

import jakarta.enterprise.context.Dependent;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@TestItercepting
@Dependent
public class TestInterceptor {
    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Exception {
        return "TestInterceptor:" + ic.proceed();
    }
}