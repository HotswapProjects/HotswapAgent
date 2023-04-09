/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
package org.hotswap.agent.util.signature;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * Checks if a Signature of a Class has changed
 *
 * @author Erki Ehtla, Vladimir Dvorak
 *
 */
public class ClassSignatureComparerHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassSignatureComparerHelper.class);

    public static String getCtClassSignature(CtClass ctClass, ClassSignatureElement[] signatureElements) throws Exception {
        CtClassSignature signature = new CtClassSignature(ctClass);
        signature.addSignatureElements(signatureElements);
        return signature.getValue();
    }

    public static String getJavaClassSignature(Class<?> clazz, ClassSignatureElement[] signatureElements) throws Exception  {
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
            String sig1 = getCtClassSignature(ctClass, signatureElements);
            String sig2 = getJavaClassSignature(clazz, signatureElements);
            return !sig1.equals(sig2);
        } catch (Exception e) {
            LOGGER.error("Error reading signature", e);
            return false;
        }
    }

    public static boolean isDifferent(Class<?> clazz1, Class<?> clazz2, ClassSignatureElement[] signatureElements) {
        try {
            String sig1 = getJavaClassSignature(clazz1, signatureElements);
            String sig2 = getJavaClassSignature(clazz2, signatureElements);
            return !sig1.equals(sig2);
        } catch (Exception e) {
            LOGGER.error("Error reading signature", e);
            return false;
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
            return false;
        }
    }

}
