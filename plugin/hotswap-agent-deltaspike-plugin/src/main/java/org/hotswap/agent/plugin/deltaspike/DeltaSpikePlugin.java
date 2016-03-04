package org.hotswap.agent.plugin.deltaspike;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.deltaspike.jsf.ViewConfigReloadCommand;
import org.hotswap.agent.plugin.deltaspike.proxy.PartialBeanClassRefreshCommand;
import org.hotswap.agent.util.AnnotationHelper;

/**
 * Apache DeltaSpike
 * @author Vladimir Dvorak
 */
@Plugin(name = "Deltaspike",
        description = "Apache DeltaSpike (http://deltaspike.apache.org/), support repository reloading",
        testedVersions = {"1.5.2"},
        expectedVersions = {"1.5.2"},
        supportClass = {DeltaSpikeTransformers.class})
public class DeltaSpikePlugin {

    private static AgentLogger LOGGER = AgentLogger.getLogger(DeltaSpikePlugin.class);

    private static final String REPOSITORY_ANNOTATION = "org.apache.deltaspike.data.api.Repository";

    private static final int WAIT_ON_REDEFINE = 100;

    @Init
    ClassLoader appClassLoader;

    @Init
    Scheduler scheduler;

    Map<Object, String> registeredRepoComponents = new WeakHashMap<Object, String>();
    Map<Object, String> registeredPartialBeans = new WeakHashMap<Object, String>();
    Map<Object, List<String>> registeredViewConfExtRootClasses = new WeakHashMap<Object, List<String>>();

    public void registerRepoComponent(Object repoComponent, Class<?> repositoryClass) {
        registeredRepoComponents.put(repoComponent, repositoryClass.getName());
        LOGGER.debug("DeltaspikePlugin - Repository Component registered : " + repositoryClass.getName());
    }

    public void registerPartialBean(Object bean, Class<?> partialBeanClass) {
        synchronized(registeredPartialBeans) {
            registeredPartialBeans.put(bean, partialBeanClass.getName());
        }
        LOGGER.debug("Registering partial bean : " + partialBeanClass.getName());
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void classReload(CtClass clazz, Class original) {
        checkRefreshPartialBean(clazz, original);
        checkRefreshViewConfigExtension(clazz, original);
    }

    private void checkRefreshPartialBean(CtClass clazz, Class original) {
        Object partialBean = getObjectByName(registeredPartialBeans, clazz.getName());
        if (partialBean != null) {

            PartialBeanClassRefreshCommand cmd = new PartialBeanClassRefreshCommand(appClassLoader, partialBean, clazz.getName());

            if (AnnotationHelper.hasAnnotation(clazz, REPOSITORY_ANNOTATION)) {
                Object repositoryComponent = getObjectByName(registeredRepoComponents, clazz.getName());
                if (repositoryComponent != null) {
                    cmd.setRepositoryComponent(repositoryComponent);
                }
            }

            scheduler.scheduleCommand(cmd, WAIT_ON_REDEFINE);
        }
    }

    private Object getObjectByName(Map<Object, String> registeredComponents, String className) {
        for (Entry<Object, String> entry : registeredComponents.entrySet()) {
           if (className.equals(entry.getValue())) {
               return entry.getKey();
           }
        }
        return null;
    }

    private void checkRefreshViewConfigExtension(CtClass clazz, Class original) {
        String className = original.getName();
        int index = className.indexOf("$");
        String rootClassName = (index!=-1) ? className.substring(0, index) : className;
        for (Entry<Object, List<String>> entry: registeredViewConfExtRootClasses.entrySet()) {
            List<String> rootClassNameList = entry.getValue();
            for (String viewConfigClassName: rootClassNameList) {
                if (viewConfigClassName.equals(rootClassName)) {
                    scheduler.scheduleCommand(new ViewConfigReloadCommand(appClassLoader, entry.getKey(), entry.getValue()), WAIT_ON_REDEFINE);
                    return;
                }
            }
        }
    }

    public void registerViewConfigRootClasses(Object viewConfigExtension, List rootClassList) {
        if (rootClassList != null ) {
            List<String> rootClassNameList = new ArrayList<String>();
            for (Object viewConfigClassObj : rootClassList) {
                Class<?> viewConfigClass = (Class<?>) viewConfigClassObj;
                LOGGER.debug("Registering ViewConfigRoot class : " + viewConfigClass.getName());
                rootClassNameList.add(viewConfigClass.getName());
            }
            registeredViewConfExtRootClasses.put(viewConfigExtension, rootClassNameList);
        }
    }

}
