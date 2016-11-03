package org.hotswap.agent.plugin.jvm.anonymous;

/**
 * Insert class $2 before class $1.
 */
public class AnonymousTestClass3 {
    public String enclosing1() {
        AnonymousTestInterface2 interface2 = new AnonymousTestInterface2() {
            @Override
            public String test2() {
                return "enclosing1: AnonymousTestClass.AnonymousTestInterface2.test2()";
            }
        };
        return interface2.test2();
    }

    public String enclosing2() {
        AnonymousTestInterface1 interface1 = new AnonymousTestInterface1() {
            @Override
            public String test1() {
                return "enclosing2: AnonymousTestClass.AnonymousTestInterface1.test1()";
            }
        };
        return interface1.test1();
    }
}
