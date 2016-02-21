package org.hotswap.agent.plugin.hotswapcommons;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.NotFoundException;

/**
 * Common hotswap tasks support. Plugin contains functionality shared among multiple plugins.
 * <p/>
 *
 * Implemented tasks:
 * <ul>
 *  <li>Flush java.beans.Introspector caches</li>
 * </ul>
 * @author Vladimir Dvorak
 */
@Plugin(name = "HotswapCommons", description = "Support for common hotswap tasks like clear Introspector caches",
        testedVersions = {"JDK 1.8.0_72"}, expectedVersions = {"JDK 1.7+"})
public class HotswapCommonsPlugin {

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    private boolean flushIntrospector;

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void classReload(CtClass clazz, Class original) {
        if (flushIntrospector) {
            scheduler.scheduleCommand(new FlushIntrospectorCommand(appClassLoader), 1000);
        }
    }


    @OnClassLoadEvent(classNameRegexp = "java.beans.WeakIdentityMap")
    public static void transformWeakIdentityMap(CtClass ctClass) throws CannotCompileException, NotFoundException {
        ctClass.addMethod(CtNewMethod.make("public void __reinitialize() {" +
                "   this.table = newTable(1<<3);;" +
                "   this.threshold = 6;" +
                "   this.size = 0;" +
                "}" +
        "}", ctClass));

    }

    public void registerFlushIntrospector() {
        flushIntrospector = true;
    }

}
