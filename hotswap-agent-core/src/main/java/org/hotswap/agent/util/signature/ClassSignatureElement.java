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

/**
 * element used to signature evaluation
 *
 * @author Erki Ehtla, Vladimir Dvorak
 */
public enum ClassSignatureElement {
    SUPER_CLASS,
    INTERFACES,
    CLASS_ANNOTATION,
    CONSTRUCTOR,
    CONSTRUCTOR_PRIVATE, // private constructors are used if CONSTRUCTOR && CONSTRUCTOR_PRIVATE are set
    METHOD,
    METHOD_PRIVATE, // private methods are used if METHOD && METHOD_PRIVATE are set
    METHOD_STATIC,  // static methods are used if METHOD && METHOD_STATIC are set
    METHOD_ANNOTATION, // applies to constructors as well
    METHOD_PARAM_ANNOTATION, // applies to constructors as well
    METHOD_EXCEPTION, // applies to constructors as well
    FIELD,
    FIELD_STATIC,  // static fields are used if FIELD && FIELD_STATIC are set
    FIELD_ANNOTATION
}
