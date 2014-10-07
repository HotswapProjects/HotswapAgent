package org.hotswap.agent.plugin.spring.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * Test aspect. While aspect reloading is not yet supported, it is used
 * to test reload of proxy around a service. It can be created by @Transactional etc.
 */
@Aspect
public class TestAspect {
    @Around("execution(* org.hotswap.agent.plugin.spring.testBeans.BeanServiceImpl.hello(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed() + "WithAspect";
    }
}
