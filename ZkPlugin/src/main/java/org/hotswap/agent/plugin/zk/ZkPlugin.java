package org.hotswap.agent.plugin.zk;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.annotation.Transform;
import org.hotswap.agent.annotation.Watch;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * ZK framework - http://www.zkoss.org/.
 * <p/>
 * <p>Plugin:<ul>
 * <li>Plugin initialization is triggered after DHtmlLayoutServlet.init() method in servlet classloader</li>
 * <li>Change default value for library properties of ZK caches
 * (org.zkoss.web.classWebResource.cache=false, org.zkoss.zk.WPD.cache=false, org.zkoss.zk.WCS.cache=false,
 * zk-dl.annotation.cache=false). App can override this setting by explicitly set value to true in zk.xml</li>
 * <li>Clear Labels cache after change of any .properties file</li>
 * <li>Clear org.zkoss.zel.BeanELResolver caches after any class change</li>
 * </ul>
 * <p/>All is invoked via reflection, no ZK lib direct dependency.
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "ZK",
        description = "ZK Framework (http://www.zkoss.org/). Change library properties default values to disable" +
                "caches, maintains Label cache and bean resolver cache.",
        testedVersions = {"6.5.2"},
        expectedVersions = {"5x", "6x", "7x?"})
public class ZkPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ZkPlugin.class);

    // clear labels cache
    ReflectionCommand refreshLabels = new ReflectionCommand(this, "org.zkoss.util.resource.Labels", "reset");

    @Init
    Scheduler scheduler;


    /**
     * Initialize the plugin after DHtmlLayoutServlet.init() method.
     */
    @Transform(classNameRegexp = "org.zkoss.zk.ui.http.DHtmlLayoutServlet")
    public static void layoutServletCallInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("init");
        init.insertAfter(PluginManagerInvoker.buildInitializePlugin(ZkPlugin.class));
    }

    /**
     * Default values of caches in development mode.
     * <p/>
     * Note, that this is a little bit aggressive, but the user may override this by providing explicit value in zk.xml
     */
    @Transform(classNameRegexp = "org.zkoss.lang.Library")
    public void defaultDisableCaches(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod m = ctClass.getDeclaredMethod("getProperty", new CtClass[]{classPool.get("java.lang.String")});

        // see http://books.zkoss.org/wiki/ZK%20Configuration%20Reference/zk.xml/The%20Library%20Properties
        defaultLibraryPropertyFalse(m, "org.zkoss.web.classWebResource.cache");
        defaultLibraryPropertyFalse(m, "org.zkoss.zk.WPD.cache");
        defaultLibraryPropertyFalse(m, "org.zkoss.zk.WCS.cache");

        // see. http://zk.datalite.cz/wiki/-/wiki/Main/DLComposer++-+MVC+Controller#section-DLComposer++-+MVC+Controller-ImplementationDetails
        defaultLibraryPropertyFalse(m, "zk-dl.annotation.cache");
    }

    private void defaultLibraryPropertyFalse(CtMethod m, String setPropertyFalse) throws CannotCompileException {
        m.insertAfter("if (_props.get(key) == null && \"" + setPropertyFalse + "\".equals(key)) return \"false\";");
    }

    @Watch(path = "/", filter = ".*.properites")
    public void refreshProperties() {
        // unable to tell if properties are ZK labels or not for custom label locator.
        // however Label refresh is very cheep, do it for any properties.
        scheduler.scheduleCommand(refreshLabels);
    }

    /**
     * BeanELResolver contains reflection cache (bean properites).
     */
    @Transform(classNameRegexp = "org.zkoss.zel.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass) throws CannotCompileException {
        String registerThis = PluginManagerInvoker.buildCallPluginMethod(ZkPlugin.class, "registerBeanELResolver",
                "this", "java.lang.Object");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerThis);
        }

        ctClass.addMethod(CtNewMethod.make("public void __resetCache() {" +
                "   this.cache = new org.zkoss.zel.BeanELResolver.ConcurrentCache(CACHE_SIZE); " +
                "}", ctClass));
    }

    Set<Object> registeredBeanELResolvers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    public void registerBeanELResolver(Object beanELResolver) {
        registeredBeanELResolvers.add(beanELResolver);
    }

    /**
     * BeanELResolver contains reflection cache (bean properites).
     */
    @Transform(classNameRegexp = "org.zkoss.bind.impl.BinderImpl")
    public static void binderImplRegisterVariable(CtClass ctClass) throws CannotCompileException {
        String registerThis = PluginManagerInvoker.buildCallPluginMethod(ZkPlugin.class, "registerBinderImpl",
                "this", "java.lang.Object");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerThis);
        }

        ctClass.addMethod(CtNewMethod.make("public void __resetCache() {" +
                "   this._initMethodCache = new org.zkoss.util.CacheMap(600,org.zkoss.util.CacheMap.DEFAULT_LIFETIME); " +
                "   this._commandMethodCache = new org.zkoss.util.CacheMap(600,org.zkoss.util.CacheMap.DEFAULT_LIFETIME); " +
                "   this._globalCommandMethodCache = new org.zkoss.util.CacheMap(600,org.zkoss.util.CacheMap.DEFAULT_LIFETIME); " +
                "}", ctClass));
    }

    Set<Object> registerBinderImpls = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    public void registerBinderImpl(Object binderImpl) {
        registerBinderImpls.add(binderImpl);
    }

    // invalidate BeanELResolver caches after any class reload (it is cheap to rebuild from reflection)
    @Transform(classNameRegexp = ".*", onDefine = false)
    public void invalidateClassCache(ClassLoader classLoader) throws Exception {
        LOGGER.debug("Refreshing ZK BeanELResolver and BinderImpl caches.");

        Method beanElResolverMethod = classLoader.loadClass("org.zkoss.zel.BeanELResolver").getDeclaredMethod("__resetCache");
        for (Object registeredBeanELResolver : registeredBeanELResolvers)
            beanElResolverMethod.invoke(registeredBeanELResolver);

        Method binderImplMethod = classLoader.loadClass("org.zkoss.bind.impl.BinderImpl").getDeclaredMethod("__resetCache");
        for (Object registerBinderImpl : registerBinderImpls)
            binderImplMethod.invoke(registerBinderImpl);
    }

}
