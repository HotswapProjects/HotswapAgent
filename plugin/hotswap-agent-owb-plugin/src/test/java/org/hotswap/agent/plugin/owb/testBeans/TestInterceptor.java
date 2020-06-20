package org.hotswap.agent.plugin.owb.testBeans;

import javax.enterprise.context.Dependent;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@TestItercepting
@Dependent
public class TestInterceptor {
    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Exception {
        return "TestInterceptor:" + ic.proceed();
    }
}