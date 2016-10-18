package org.hotswap.agent.plugin.weld;

public enum BeanReloadStrategy {
    CLASS_CHANGE,
    METHOD_FIELD_SIGNATURE_CHANGE,
    FIELD_SIGNATURE_CHANGE,
    NEVER
}
