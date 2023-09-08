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
package org.hotswap.agent.plugin.spring.utils;

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
