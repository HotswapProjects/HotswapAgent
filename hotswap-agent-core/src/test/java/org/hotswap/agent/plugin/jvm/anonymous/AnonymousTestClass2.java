package org.hotswap.agent.plugin.jvm.anonymous;

/**
 * Change of enclosing method name.
 */
public class AnonymousTestClass2 {
    public String enclosing2() {
        AnonymousTestInterface1 interface1 = new AnonymousTestInterface1() {
            @Override
            public String test1() {
                return "enclosing2: AnonymousTestClass1.AnonymousTestInterface1.test1()";
            }
        };
        return interface1.test1();
    }
}
