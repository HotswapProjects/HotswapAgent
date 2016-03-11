package org.hotswap.agent.plugin.weld;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.signature.ClassSignatureComparerHelper;
import org.hotswap.agent.util.signature.ClassSignatureElement;

/**
 * Creates signature for Weld proxy class. Signature calculation uses class elements defined in SIGNATURE_ELEMENTS.
 *
 * @author Vladimir Dvorak
 */
public class ProxyClassSignatureHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ProxyClassSignatureHelper.class);

    private static final ClassSignatureElement[] SIGNATURE_ELEMENTS = {
            ClassSignatureElement.SUPER_CLASS,
            ClassSignatureElement.INTERFACES,
            ClassSignatureElement.CLASS_ANNOTATION,
            ClassSignatureElement.CONSTRUCTOR,
            ClassSignatureElement.METHOD,
            ClassSignatureElement.METHOD_ANNOTATION,
            ClassSignatureElement.METHOD_PARAM_ANNOTATION,
            ClassSignatureElement.METHOD_EXCEPTION,
            ClassSignatureElement.FIELD,
            ClassSignatureElement.FIELD_ANNOTATION
    };

    /**
     * Gets the java class signature.
     *
     * @param clazz the clazz for which signature is calculated
     * @return the java class signature
     */
    public static String getJavaClassSignature(Class<?> clazz) {
        try {
            return ClassSignatureComparerHelper.getJavaClassSignature(clazz, SIGNATURE_ELEMENTS);
        } catch (Exception e) {
            LOGGER.error("Error reading signature", e);
            return null;
        }
    }
}
