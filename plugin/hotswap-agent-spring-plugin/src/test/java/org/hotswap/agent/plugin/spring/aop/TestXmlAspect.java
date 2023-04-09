/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * Test aspect. While aspect reloading is not yet supported, it is used
 * to test reload of proxy around a service. It can be created by @Transactional etc.
 */
@Aspect
public class TestXmlAspect {
    @Around("execution(* org.hotswap.agent.plugin.spring.testBeans.BeanServiceNoAutowireImpl.hello(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed() + "WithAspect";
    }
}
