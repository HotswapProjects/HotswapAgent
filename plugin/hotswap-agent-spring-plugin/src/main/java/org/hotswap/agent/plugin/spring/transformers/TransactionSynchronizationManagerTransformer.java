/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.transformers;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.expr.ExprEditor;
import org.hotswap.agent.javassist.expr.MethodCall;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.core.TransactionResourceMap;

/**
 * Lets the TransactionSynchronizationManager find a resource bound to a key whose hash code a reload has changed,
 * see {@link TransactionResourceMap}.
 */
public class TransactionSynchronizationManagerTransformer {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(TransactionSynchronizationManagerTransformer.class);

    /**
     * The manager keeps its resources in a map. The lookups in it go through TransactionResourceMap: Map.get() and
     * Map.remove() in doGetResource and doUnbindResource, the same from Spring 3.1 to 7.0, also in the manager of
     * reactive transactions.
     * <p>
     * Map.put() in bindResource is left as it is: a key that is still bound after its hash code has changed is
     * bound again without the error "Already value [...] bound to thread", and bindSynchronizedResource of Spring 7
     * does not replace its old value. The old value stays bound until the key is unbound twice.
     */
    @OnClassLoadEvent(classNameRegexp = "org.springframework.transaction.(support|reactive).TransactionSynchronizationManager")
    public static void transform(CtClass clazz) throws CannotCompileException {
        final String resourceMap = TransactionResourceMap.class.getName();
        final int[] replaced = new int[2];
        clazz.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (!"java.util.Map".equals(m.getClassName())
                        || !"(Ljava/lang/Object;)Ljava/lang/Object;".equals(m.getSignature())) {
                    return;
                }
                if ("get".equals(m.getMethodName())) {
                    m.replace("{ $_ = " + resourceMap + ".get($0, $1); }");
                    replaced[0]++;
                } else if ("remove".equals(m.getMethodName())) {
                    m.replace("{ $_ = " + resourceMap + ".remove($0, $1); }");
                    replaced[1]++;
                }
            }
        });
        if (replaced[0] == 0 || replaced[1] == 0) {
            LOGGER.warning("The lookup of transaction resources was not found in the class '{}' of this Spring version. "
                    + "A transaction may fail after a reload that recreates its data source.", clazz.getName());
        } else {
            LOGGER.debug("class '{}' patched with the lookup of resources whose key has changed.", clazz.getName());
        }
    }
}
