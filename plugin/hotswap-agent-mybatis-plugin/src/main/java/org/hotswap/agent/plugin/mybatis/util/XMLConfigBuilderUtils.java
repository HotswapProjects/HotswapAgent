package org.hotswap.agent.plugin.mybatis.util;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.NotFoundException;

public class XMLConfigBuilderUtils {
    public static CtConstructor getBuilderInstrumentConstructor(CtClass ctClass, ClassPool classPool) throws NotFoundException {
        CtClass[] constructorParams = new CtClass[]{
                classPool.get("org.apache.ibatis.parsing.XPathParser"),
                classPool.get("java.lang.String"),
                classPool.get("java.util.Properties")
        };

        CtClass[] newConstructorParams = new CtClass[]{
                classPool.get("java.lang.Class"),
                classPool.get("org.apache.ibatis.parsing.XPathParser"),
                classPool.get("java.lang.String"),
                classPool.get("java.util.Properties")
        };

        CtConstructor instrumentConstructor = null;
        CtConstructor[] declaredConstructors = ctClass.getDeclaredConstructors();

        for (CtConstructor declaredConstructor : declaredConstructors) {
            CtClass[] parameterTypes = declaredConstructor.getParameterTypes();
            if (isArgMatch(constructorParams, parameterTypes)) {
                instrumentConstructor = declaredConstructor;
                break;
            }

            if (isArgMatch(newConstructorParams, parameterTypes)) {
                instrumentConstructor = declaredConstructor;
                break;
            }

        }

        return instrumentConstructor;
    }

    private static boolean isArgMatch(CtClass[] constructorParams,  CtClass[] parameterTypes) {
        if (parameterTypes.length != constructorParams.length) {
            return false;
        }

        for (int i = 0; i < parameterTypes.length; i++) {
            if (!parameterTypes[i].getName().equals(constructorParams[i].getName())) {
                return false;
            }
        }

        return true;
    }
}
