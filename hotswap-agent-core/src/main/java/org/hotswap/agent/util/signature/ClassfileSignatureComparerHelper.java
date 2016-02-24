package org.hotswap.agent.util.signature;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Checks if a Signature of a Class has changed
 *
 * @author Erki Ehtla
 *
 */
public class ClassfileSignatureComparerHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassfileSignatureComparerHelper.class);

    public static ClassSignatureValue getCtClassSignature(CtClass ctClass, ClassSignatureElement[] signatureElements) throws Exception {
        CtClassSignature signature = new CtClassSignature(ctClass);
        signature.addSignatureElements(signatureElements);
        return signature.getValue();
    }

    public static ClassSignatureValue getJavaClassSignature(Class<?> clazz, ClassSignatureElement[] signatureElements) throws Exception  {
        JavaClassSignature signature = new JavaClassSignature(clazz);
        signature.addSignatureElements(signatureElements);
        return signature.getValue();
    }

    /**
     * @param clazz old Class definition
     * @param cp ClassPool which should contain the new/compared definition
     * @return is signature different
     */
    public static boolean isPoolClassDifferent(Class<?> clazz, ClassPool cp, ClassSignatureElement[] signatureElements) {
        try {
            ClassSignatureValue ctClassSignature = getCtClassSignature(cp.get(clazz.getName()), signatureElements);
            ClassSignatureValue javaClassSignature = getJavaClassSignature(clazz, signatureElements);
            return !ctClassSignature.equals(javaClassSignature);
        } catch (Exception e) {
            LOGGER.error("Error reading siganture", e);
            return false;
        }
    }

}
