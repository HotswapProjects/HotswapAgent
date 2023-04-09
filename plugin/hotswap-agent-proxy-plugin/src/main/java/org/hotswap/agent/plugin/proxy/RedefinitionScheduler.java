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
package org.hotswap.agent.plugin.proxy;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.plugin.proxy.api.MultistepProxyTransformer;

/**
 * Schedules a new redefiniton event for MultistepProxyTransformer
 *
 * @author Erki Ehtla
 */
public final class RedefinitionScheduler implements Runnable {
    private MultistepProxyTransformer transformer;

    @Init
    private static Instrumentation instrumentation;

    public RedefinitionScheduler(MultistepProxyTransformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public void run() {
        try {
            instrumentation.redefineClasses(new ClassDefinition(transformer.getClassBeingRedefined(), transformer
                    .getClassfileBuffer()));
        } catch (Throwable t) {
            transformer.removeClassState();
            throw new RuntimeException(t);
        }
    }

    public static void schedule(MultistepProxyTransformer multistepProxyTransformer) {
        new Thread(new RedefinitionScheduler(multistepProxyTransformer)).start();
    }
}