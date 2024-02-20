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
