package org.hotswap.agent.util.classloader;

import org.hotswap.agent.javassist.CtClass;

/**
 * @author Jiri Bubnik
 */
@Deprecated
public class ClassLoaderProxy {

    ClassLoader targetClassLoader;

    public CtClass create(CtClass classToProxy) throws Exception {
//        CtPool ctPool = classToProxy;
//        ProxyFactory factory = new ProxyFactory();
//        factory.setSuperclass(classToProxy);
//        factory.
//
//        Class proxy = factory.createClass();
//
//        new ClassFile()
//
//
//        MethodHandler handler = new MethodHandler() {
//
//            @Override
//            public Object invoke(Object self, Method overridden, Method forwarder,
//                                 Object[] args) throws Throwable {
//                System.out.println("do something "+overridden.getName());
//
//                Class classInTargetClassLoader = targetClassLoader.loadClass(classToProxy.getName());
//                Method methodInTargetClassLoader = classInTargetClassLoader.getDeclaredMethod(
//                        overridden.getName(), overridden.getParameterTypes()
//                );
//
//                Class returnType = overridden.getReturnType();
//
//                return methodInTargetClassLoader.invoke(null, args);
//            }
//        };
//        Object instance = proxy.newInstance();
//        ((ProxyObject) instance).setHandler(handler);
//        return (T) instance;
        return null;
    }

}
