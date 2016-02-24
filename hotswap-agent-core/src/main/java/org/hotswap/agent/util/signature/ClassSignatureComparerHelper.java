package org.hotswap.agent.util.signature;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Checks if a Signature of a Class has changed
 *
 * @author Erki Ehtla
 *
 */
public class ClassSignatureComparerHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassSignatureComparerHelper.class);

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
     * @param ctClass new CtClass definition
     * @param clazz old Class definition
     * @return is signature different
     */
    public static boolean isDifferent(CtClass ctClass, Class<?> clazz, ClassSignatureElement[] signatureElements) {
        try {
            ClassSignatureValue newSignature = getCtClassSignature(ctClass, signatureElements);
            ClassSignatureValue oldSignatue = getJavaClassSignature(clazz, signatureElements);
            return !newSignature.equals(oldSignatue);
        } catch (Exception e) {
            LOGGER.error("Error reading siganture", e);
            return true;
        }
    }

    /**
     * @param clazz old Class definition
     * @param cp ClassPool which should contain the new/compared definition
     * @return is signature different
     */
    public static boolean isPoolClassDifferent(Class<?> clazz, ClassPool cp, ClassSignatureElement[] signatureElements) {
        try {
            return isDifferent(cp.get(clazz.getName()), clazz, signatureElements);
        } catch (NotFoundException e) {
            LOGGER.error("Class not found ", e);
            return true;
        }
    }

}
