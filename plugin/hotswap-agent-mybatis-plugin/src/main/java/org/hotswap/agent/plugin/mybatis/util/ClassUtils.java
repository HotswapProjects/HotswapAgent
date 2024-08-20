package org.hotswap.agent.plugin.mybatis.util;

import org.hotswap.agent.javassist.*;

public class ClassUtils {
    public static void addFieldNotExists(CtClass ctClass, CtField ctField) throws CannotCompileException {
        if (fieldExists(ctClass, ctField.getName())) {
            return;
        }
        ctClass.addField(ctField);
    }

    public static boolean fieldExists(CtClass ctClass, String fieldName) {
        try {
            return ctClass.getDeclaredField(fieldName) != null;
        } catch (NotFoundException e) {
            return false;
        }
    }

    public static boolean methodExists(CtClass ctClass, String methodName, CtClass[] params) {
        try {
            return ctClass.getDeclaredMethod(methodName, params) != null;
        } catch (NotFoundException e) {
            return false;
        }
    }
}
