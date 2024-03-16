/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.transformers.support;

public class InitMethodEnhance {

    public static String catchException(String objectName, String loggerName, String exceptionName, String from,
        boolean hasReturnValue) {
        return "{"
            + "if (org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant.getBeanFactoryAssistant($0) == null || "
            + " !org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant.getBeanFactoryAssistant($0).isReload()) {"
            + "     throw " + exceptionName + " ; }"
            + "else {"
            + "     " + loggerName + ".warning(\"Failed to invoke init method of {} from {}: {}\", "
                    + "new java.lang.Object[]{" + objectName + ".getClass().getName(),\""
                    + from + "\", "+ exceptionName + ".getMessage()});"
            + "     " + loggerName + ".debug(\"the detail reason as following{}: \", " + exceptionName
                    + ", new java.lang.Object[]{ \" \"});" + (hasReturnValue ? " return " + objectName + ";" : "return;")
            + " } "
            + "}";
    }
}
