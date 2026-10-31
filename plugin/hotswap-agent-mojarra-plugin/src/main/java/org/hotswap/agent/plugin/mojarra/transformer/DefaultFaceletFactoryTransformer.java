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
package org.hotswap.agent.plugin.mojarra.transformer;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mojarra.ExtraWebappRoots;

/**
 * Makes Facelets served from the directories HotswapAgent puts on top of the web application (webappDir,
 * watchResources, extraClasspath) behave as part of the application in Mojarra 4.0.27+ / 4.1.13+, see #684:
 * <ul>
 * <li>requireWithinApplicationRoot - relative includes and templates are not rejected as
 * "not within the application root"</li>
 * <li>getWebappPath - the files get their path in the web application, so the resource library contracts access
 * check sees a contract's own Facelets as contract resources</li>
 * </ul>
 * Older Mojarra versions do not have these methods and are not patched.
 */
public class DefaultFaceletFactoryTransformer {

    private static final AgentLogger LOGGER = AgentLogger.getLogger(DefaultFaceletFactoryTransformer.class);

    static final String DEFAULT_FACELET_FACTORY = "com.sun.faces.facelets.impl.DefaultFaceletFactory";

    @OnClassLoadEvent(classNameRegexp = DEFAULT_FACELET_FACTORY)
    public static void patchDefaultFaceletFactory(ClassPool classPool, CtClass ctClass)
            throws CannotCompileException, NotFoundException {
        CtClass urlClass = classPool.get("java.net.URL");
        CtMethod requireWithinApplicationRoot = findMethod(ctClass, "requireWithinApplicationRoot",
                urlClass, classPool.get("java.lang.String"));
        if (requireWithinApplicationRoot == null) {
            LOGGER.debug("{} has no requireWithinApplicationRoot method, not patched.", DEFAULT_FACELET_FACTORY);
            return;
        }

        String roots = ExtraWebappRoots.class.getName();
        ctClass.addField(CtField.make("private String[] $$ha$extraWebappRoots;", ctClass));
        ctClass.addMethod(CtNewMethod.make(
                "private String[] hotswapAgent$extraWebappRoots() {" +
                "   if (this.$$ha$extraWebappRoots == null) {" +
                "       this.$$ha$extraWebappRoots = " + roots + ".resolve(Thread.currentThread().getContextClassLoader());" +
                "   }" +
                "   return this.$$ha$extraWebappRoots;" +
                "}", ctClass));

        requireWithinApplicationRoot.insertBefore(
                "{" +
                "   if (" + roots + ".contains(hotswapAgent$extraWebappRoots(), $1)) {" +
                "       return;" +
                "   }" +
                "}");
        LOGGER.debug("Patched {}.requireWithinApplicationRoot", DEFAULT_FACELET_FACTORY);

        CtMethod getWebappPath = findMethod(ctClass, "getWebappPath", urlClass);
        if (getWebappPath != null) {
            getWebappPath.insertBefore(
                    "{" +
                    "   String haWebappPath = " + roots + ".webappPath(hotswapAgent$extraWebappRoots(), $1);" +
                    "   if (haWebappPath != null) {" +
                    "       return haWebappPath;" +
                    "   }" +
                    "}");
            LOGGER.debug("Patched {}.getWebappPath", DEFAULT_FACELET_FACTORY);
        }
    }

    private static CtMethod findMethod(CtClass ctClass, String name, CtClass... params) {
        try {
            return ctClass.getDeclaredMethod(name, params);
        } catch (NotFoundException e) {
            return null;
        }
    }
}
