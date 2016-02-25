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
