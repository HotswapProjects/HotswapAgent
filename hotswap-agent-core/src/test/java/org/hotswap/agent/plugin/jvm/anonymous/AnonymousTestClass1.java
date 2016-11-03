package org.hotswap.agent.plugin.jvm.anonymous;

/**
 * Created by bubnik on 17.11.13.
 */
public class AnonymousTestClass1 {
    public String enclosing1() {
        AnonymousTestInterface1 interface1 = new AnonymousTestInterface1() {
            @Override
            public String test1() {
                return "enclosing1: AnonymousTestClass1.AnonymousTestInterface1.test1()";
            }
        };
        return interface1.test1();
    }
}
