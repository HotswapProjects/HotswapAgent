package org.hotswap.agent.plugin.jvm.classinit;

public class StaticTest2 {
    static int int1 = 20; // Changed from 10 -> 20
    static int int2 = 20;
    static String str1 = "str2";
    static String str2 = "str2";
    static Object obj1 = new String("obj2");
    static Object obj2 = new String("obj2");
}
