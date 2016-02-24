package org.hotswap.agent.plugin.spring.signature;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.util.signature.ClassSignatureElement;
import org.hotswap.agent.util.signature.ClassSignatureComparerHelper;

/**
 * Checks if a Signature of a Class has changed enough to necessitate a Spring reload.
 *
 * @author Erki Ehtla, Vladimir Dvorak
 *
 */
public class ClassSignatureComparer {

    private static final ClassSignatureElement[] SIGNATURE_ELEMENTS=  {
            ClassSignatureElement.SUPER_CLASS,
            ClassSignatureElement.INTERFACES,
            ClassSignatureElement.CLASS_ANNOTATION,
            ClassSignatureElement.CONSTRUCTOR,
            ClassSignatureElement.METHOD,
            ClassSignatureElement.METHOD_STATIC,
            ClassSignatureElement.METHOD_ANNOTATION,
            ClassSignatureElement.METHOD_PARAM_ANNOTATION,
            ClassSignatureElement.METHOD_EXCEPTION,
            ClassSignatureElement.FIELD,
            ClassSignatureElement.FIELD_STATIC,
            ClassSignatureElement.FIELD_ANNOTATION
    };

    public static boolean isPoolClassDifferent(Class<?> classBeingRedefined, ClassPool cp) {
        return ClassSignatureComparerHelper.isPoolClassDifferent(classBeingRedefined, cp, SIGNATURE_ELEMENTS);
    }
}
