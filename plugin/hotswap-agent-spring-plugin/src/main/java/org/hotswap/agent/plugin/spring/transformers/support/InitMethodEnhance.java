package org.hotswap.agent.plugin.spring.transformers.support;

public class InitMethodEnhance {

    public static String catchException(String objectName, String loggerName, String exceptionName,
        boolean hasReturnValue) {
        return "{"
            + "if (org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant.getBeanFactoryAssistant($0) == null || "
            + "!org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant.getBeanFactoryAssistant($0).isReload"
            + "()) {"
            + " throw " + exceptionName + " ; }"
            + "else {"
            + loggerName + ".warning(\"Failed to invoke @PostConstruct method: {}\", new java.lang.Object[]{"
            + objectName + ".getClass().getName()});"
            + (hasReturnValue ? " return " + objectName + ";" : "return;") + " }";
    }
}
