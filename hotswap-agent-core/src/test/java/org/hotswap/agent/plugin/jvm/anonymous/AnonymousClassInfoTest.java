package org.hotswap.agent.plugin.jvm.anonymous;

import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.LoaderClassPath;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.plugin.jvm.AnonymousClassInfo;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Check generated signatures same with java Class and javassist CtClass.
 */
public class AnonymousClassInfoTest {

    @Test
    public void testGetClassSignature() throws Exception {
        String classSignature = "java.lang.Object;" + AnonymousTestInterface1.class.getName();
        assertEquals(classSignature, getAnonymousClassInfo().getClassSignature());
        assertEquals(classSignature, getAnonymousCtClassInfo().getClassSignature());
    }

    @Test
    public void testGetMethodSignature() throws Exception {
        String methodsSignature = "java.lang.String test1();";
        assertEquals(methodsSignature, getAnonymousClassInfo().getMethodSignature());
        assertEquals(methodsSignature, getAnonymousCtClassInfo().getMethodSignature());
    }

    @Test
    public void testGetFieldsSignature() throws Exception {
        // default field this
        String fieldsSignature = "org.hotswap.agent.plugin.jvm.anonymous.AnonymousTestClass1 this$0;";
        assertEquals(fieldsSignature, getAnonymousClassInfo().getFieldsSignature());
        assertEquals(fieldsSignature, getAnonymousCtClassInfo().getFieldsSignature());
    }

    @Test
    public void testGetEnclosingMethodSignature() throws Exception {
        String enclosingMethodSignature = "java.lang.String enclosing1();";
        assertEquals(enclosingMethodSignature, getAnonymousClassInfo().getEnclosingMethodSignature());
        assertEquals(enclosingMethodSignature, getAnonymousCtClassInfo().getEnclosingMethodSignature());
    }

    private AnonymousClassInfo getAnonymousClassInfo() throws ClassNotFoundException {
        Class clazz = getClass().getClassLoader().loadClass(AnonymousTestClass1.class.getName() + "$1");
        return new AnonymousClassInfo(clazz);
    }

    public AnonymousClassInfo getAnonymousCtClassInfo() throws NotFoundException, ClassNotFoundException, IOException, CannotCompileException {
        Class clazz = getClass().getClassLoader().loadClass(AnonymousTestClass1.class.getName() + "$1");

        ClassPool classPool = new ClassPool();
        classPool.appendClassPath(new LoaderClassPath(getClass().getClassLoader()));
        return new AnonymousClassInfo(classPool.get(clazz.getName()));
    }
}
