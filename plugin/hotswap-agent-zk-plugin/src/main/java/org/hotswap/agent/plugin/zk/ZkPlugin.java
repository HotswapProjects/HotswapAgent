package org.hotswap.agent.plugin.zk;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.*;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
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

    @Init
    ClassLoader appClassLoader;


    /**
     * Initialize the plugin after DHtmlLayoutServlet.init() method.
     */
    @OnClassLoadEvent(classNameRegexp = "org.zkoss.zk.ui.http.DHtmlLayoutServlet")
    public static void layoutServletCallInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("init");
        init.insertAfter(PluginManagerInvoker.buildInitializePlugin(ZkPlugin.class));
        LOGGER.debug("org.zkoss.zk.ui.http.DHtmlLayoutServlet enahnced with plugin initialization.");
    }

    /**
     * Default values of caches in development mode.
     * <p/>
     * Note, that this is a little bit aggressive, but the user may override this by providing explicit value in zk.xml
     */
    @OnClassLoadEvent(classNameRegexp = "org.zkoss.lang.Library")
    public static void defaultDisableCaches(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        LOGGER.debug("org.zkoss.lang.Library enhanced to replace property '*.cache' default value to 'false'.");
        CtMethod m = ctClass.getDeclaredMethod("getProperty", new CtClass[]{classPool.get("java.lang.String")});

        // see http://books.zkoss.org/wiki/ZK%20Configuration%20Reference/zk.xml/The%20Library%20Properties
        defaultLibraryPropertyFalse(m, "org.zkoss.web.classWebResource.cache");
        defaultLibraryPropertyFalse(m, "org.zkoss.zk.WPD.cache");
        defaultLibraryPropertyFalse(m, "org.zkoss.zk.WCS.cache");

        // see. http://zk.datalite.cz/wiki/-/wiki/Main/DLComposer++-+MVC+Controller#section-DLComposer++-+MVC+Controller-ImplementationDetails
        defaultLibraryPropertyFalse(m, "zk-dl.annotation.cache");
    }

    private static void defaultLibraryPropertyFalse(CtMethod m, String setPropertyFalse) throws CannotCompileException {
        m.insertAfter("if (_props.get(key) == null && \"" + setPropertyFalse + "\".equals(key)) return \"false\";");
    }

    @OnResourceFileEvent(path = "/", filter = ".*.properites")
    public void refreshProperties() {
        // unable to tell if properties are ZK labels or not for custom label locator.
        // however Label refresh is very cheep, do it for any properties.
        scheduler.scheduleCommand(refreshLabels);
    }

    /**
     * BeanELResolver contains reflection cache (bean properites).
     */
    @OnClassLoadEvent(classNameRegexp = "org.zkoss.zel.BeanELResolver")
    public static void beanELResolverRegisterVariable(CtClass ctClass) throws CannotCompileException {
        String registerThis = PluginManagerInvoker.buildCallPluginMethod(ZkPlugin.class, "registerBeanELResolver",
                "this", "java.lang.Object");
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(registerThis);
        }

        ctClass.addMethod(CtNewMethod.make("public void __resetCache() {" +
                "   this.cache = new org.zkoss.zel.BeanELResolver.ConcurrentCache(CACHE_SIZE); " +
                "}", ctClass));

        LOGGER.debug("org.zkoss.zel.BeanELResolver - added method __resetCache().");
    }

    Set<Object> registeredBeanELResolvers = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    public void registerBeanELResolver(Object beanELResolver) {
        registeredBeanELResolvers.add(beanELResolver);
    }

    /**
     * BeanELResolver contains reflection cache (bean properites).
     */
    @OnClassLoadEvent(classNameRegexp = "org.zkoss.bind.impl.BinderImpl")
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

        LOGGER.debug("org.zkoss.bind.impl.BinderImpl - added method __resetCache().");
    }

    Set<Object> registerBinderImpls = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    public void registerBinderImpl(Object binderImpl) {
        registerBinderImpls.add(binderImpl);
    }

    // invalidate BeanELResolver caches after any class reload (it is cheap to rebuild from reflection)
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateClassCache() throws Exception {
        scheduler.scheduleCommand(invalidateClassCache);
    }

    // schedule refresh in case of multiple class redefinition to merge command executions
    private Command invalidateClassCache = new Command() {
        @Override
        public void executeCommand() {
            LOGGER.debug("Refreshing ZK BeanELResolver and BinderImpl caches.");

            try {
                Method beanElResolverMethod = resolveClass("org.zkoss.zel.BeanELResolver").getDeclaredMethod("__resetCache");
                for (Object registeredBeanELResolver : registeredBeanELResolvers) {
                    LOGGER.trace("Invoking org.zkoss.zel.BeanELResolver.__resetCache on {}", registeredBeanELResolver);
                    beanElResolverMethod.invoke(registeredBeanELResolver);
                }

                Method binderImplMethod = resolveClass("org.zkoss.bind.impl.BinderImpl").getDeclaredMethod("__resetCache");
                for (Object registerBinderImpl : registerBinderImpls)
                    binderImplMethod.invoke(registerBinderImpl);

                Field afterComposeMethodCache = resolveClass("org.zkoss.bind.BindComposer").getDeclaredField("_afterComposeMethodCache");
                afterComposeMethodCache.setAccessible(true);
                ((Map)afterComposeMethodCache.get(null)).clear();
            } catch (Exception e) {
                LOGGER.error("Error refreshing ZK BeanELResolver and BinderImpl caches.", e);
            }
        }
    };

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
