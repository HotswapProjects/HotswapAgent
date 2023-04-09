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
package org.hotswap.agent.plugin.weld;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.signature.ClassSignatureComparerHelper;
import org.hotswap.agent.util.signature.ClassSignatureElement;

/**
 * Creates signature for Weld proxy class. Signature calculation uses class elements defined in SIGNATURE_ELEMENTS.
 *
 * @author Vladimir Dvorak
 */
public class WeldClassSignatureHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(WeldClassSignatureHelper.class);

    private static final ClassSignatureElement[] SIGNATURE_ELEM_PROXY = {
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

    private static final ClassSignatureElement[] SIGNATURE_ELEM_METHOD_FIELDS = {
            ClassSignatureElement.SUPER_CLASS,
            ClassSignatureElement.INTERFACES,
            ClassSignatureElement.CLASS_ANNOTATION,
            ClassSignatureElement.CONSTRUCTOR,
            ClassSignatureElement.CONSTRUCTOR_PRIVATE, // private constructors are used if CONSTRUCTOR && CONSTRUCTOR_PRIVATE are set
            ClassSignatureElement.METHOD,
            ClassSignatureElement.METHOD_PRIVATE, // private methods are used if METHOD && METHOD_PRIVATE are set
            ClassSignatureElement.METHOD_ANNOTATION, // applies to constructors as well
            ClassSignatureElement.METHOD_PARAM_ANNOTATION, // applies to constructors as well
            ClassSignatureElement.FIELD,
            ClassSignatureElement.FIELD_ANNOTATION
    };

    private static final ClassSignatureElement[] SIGNATURE_ELEM_FIELDS = {
            ClassSignatureElement.FIELD,
            ClassSignatureElement.FIELD_ANNOTATION
    };

    /**
     * Gets the class signature for proxy class comparison
     *
     * @param clazz the clazz for which signature is calculated
     * @return the java class signature
     */
    public static String getSignatureForProxyClass(Class<?> clazz) {
        try {
            return ClassSignatureComparerHelper.getJavaClassSignature(clazz, SIGNATURE_ELEM_PROXY);
        } catch (Exception e) {
            LOGGER.error("getSignatureForProxyClass(): Error reading signature", e);
            return null;
        }
    }

    /**
     * Gets the signature by strategy.
     *
     * @param strategy the strategy
     * @param clazz the clazz
     * @return the signature by strategy
     */
    public static String getSignatureByStrategy(BeanReloadStrategy strategy, Class<?> clazz) {
        if (strategy == null) {
            strategy = BeanReloadStrategy.NEVER;
        }
        switch (strategy) {
        case CLASS_CHANGE :
            return null;
        case METHOD_FIELD_SIGNATURE_CHANGE :
            return getClassMethodFieldsSignature(clazz);
        case FIELD_SIGNATURE_CHANGE :
            return getClassFieldsSignature(clazz);
        default:
        case NEVER:
            return null;
        }
    }

    private static String getClassMethodFieldsSignature(Class<?> clazz) {
        try {
            return ClassSignatureComparerHelper.getJavaClassSignature(clazz, SIGNATURE_ELEM_METHOD_FIELDS);
        } catch (Exception e) {
            LOGGER.error("getSignatureForProxyClass(): Error reading signature", e);
            return null;
        }
    }

    private static String getClassFieldsSignature(Class<?> clazz) {
        try {
            return ClassSignatureComparerHelper.getJavaClassSignature(clazz, SIGNATURE_ELEM_FIELDS);
        } catch (Exception e) {
            LOGGER.error("getSignatureForProxyClass(): Error reading signature", e);
            return null;
        }
    }
}
