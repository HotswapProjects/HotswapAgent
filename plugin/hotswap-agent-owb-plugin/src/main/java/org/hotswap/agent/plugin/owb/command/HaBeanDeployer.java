package org.hotswap.agent.plugin.owb.command;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Priority;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.ObserverMethod;

import org.apache.webbeans.component.BeanAttributesImpl;
import org.apache.webbeans.component.CdiInterceptorBean;
import org.apache.webbeans.component.DecoratorBean;
import org.apache.webbeans.component.InjectionTargetBean;
import org.apache.webbeans.component.ManagedBean;
import org.apache.webbeans.component.ProducerFieldBean;
import org.apache.webbeans.component.ProducerMethodBean;
import org.apache.webbeans.component.creation.BeanAttributesBuilder;
import org.apache.webbeans.component.creation.CdiInterceptorBeanBuilder;
import org.apache.webbeans.component.creation.DecoratorBeanBuilder;
import org.apache.webbeans.component.creation.ManagedBeanBuilder;
import org.apache.webbeans.component.creation.ObserverMethodsBuilder;
import org.apache.webbeans.component.creation.ProducerFieldBeansBuilder;
import org.apache.webbeans.component.creation.ProducerMethodBeansBuilder;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.decorator.DecoratorsManager;
import org.apache.webbeans.event.ObserverMethodImpl;
import org.apache.webbeans.intercept.InterceptorsManager;
import org.apache.webbeans.portable.AnnotatedElementFactory;
import org.apache.webbeans.portable.events.ProcessBeanImpl;
import org.apache.webbeans.portable.events.generics.GProcessManagedBean;
import org.apache.webbeans.util.WebBeansUtil;
import org.hotswap.agent.logging.AgentLogger;

/**
 * The Class HaBeanDeployer. Based on code from BeanDeployer
 *
 * @author Vladimir Dvorak
 */
public class HaBeanDeployer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(HaBeanDeployer.class);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void doDefineManagedBean(BeanManagerImpl beanManager, Class<?> beanClass) {

        WebBeansContext wbc = beanManager.getWebBeansContext();

        AnnotatedElementFactory annotatedElementFactory = wbc.getAnnotatedElementFactory();
        // Clear AnnotatedElementFactory caches (is it necessary for definition ?)
        annotatedElementFactory.clear();

        // Injection resolver cache must be cleared before / after definition
        beanManager.getInjectionResolver().clearCaches();

        AnnotatedType annotatedType = annotatedElementFactory.newAnnotatedType(beanClass);
        BeanAttributesImpl attributes = BeanAttributesBuilder.forContext(wbc).newBeanAttibutes(annotatedType).build();

        if(wbc.getWebBeansUtil().supportsJavaEeComponentInjections(beanClass)) {
            //Fires ProcessInjectionTarget
            wbc.getWebBeansUtil().fireProcessInjectionTargetEventForJavaEeComponents(beanClass).setStarted();
            wbc.getWebBeansUtil().inspectDeploymentErrorStack(
                    "There are errors that are added by ProcessInjectionTarget event observers. Look at logs for further details");
            //Checks that not contains @Inject InjectionPoint
            wbc.getAnnotationManager().checkInjectionPointForInjectInjectionPoint(beanClass);
        }

        {
            ManagedBeanBuilder managedBeanCreator = new ManagedBeanBuilder(wbc, annotatedType, attributes);
            DecoratorsManager decoratorsManager = wbc.getDecoratorsManager();
            InterceptorsManager interceptorsManager = wbc.getInterceptorsManager();

            if(WebBeansUtil.isDecorator(annotatedType)) {

                LOGGER.debug("Found Managed Bean Decorator with class name : [{}]", annotatedType.getJavaClass().getName());

                DecoratorBeanBuilder dbb = new DecoratorBeanBuilder(wbc, annotatedType, attributes);
                if (dbb.isDecoratorEnabled()) {
                    dbb.defineDecoratorRules();
                    DecoratorBean decorator = dbb.getBean();
                    decoratorsManager.addDecorator(decorator);
                }

            } else if(WebBeansUtil.isCdiInterceptor(annotatedType)) {
                LOGGER.debug("Found Managed Bean Interceptor with class name : [{}]", annotatedType.getJavaClass().getName());

                CdiInterceptorBeanBuilder ibb = new CdiInterceptorBeanBuilder(wbc, annotatedType, attributes);

                if (ibb.isInterceptorEnabled()) {
                    ibb.defineCdiInterceptorRules();
                    CdiInterceptorBean interceptor = (CdiInterceptorBean) ibb.getBean();
                    interceptorsManager.addCdiInterceptor(interceptor);
                }
            } else {
                InjectionTargetBean bean = managedBeanCreator.getBean();

                if (decoratorsManager.containsCustomDecoratorClass(annotatedType.getJavaClass()) ||
                        interceptorsManager.containsCustomInterceptorClass(annotatedType.getJavaClass())) {
                    return; //TODO discuss this case (it was ignored before)
                }

                LOGGER.debug("Found Managed Bean with class name : [{}]", annotatedType.getJavaClass().getName());

                Set<ObserverMethod<?>> observerMethods;
                AnnotatedType beanAnnotatedType = bean.getAnnotatedType();
//                AnnotatedType defaultAt = webBeansContext.getAnnotatedElementFactory().getAnnotatedType(beanAnnotatedType.getJavaClass());
                boolean ignoreProducer = false /*defaultAt != beanAnnotatedType && annotatedTypes.containsKey(defaultAt)*/;

                if(bean.isEnabled()) {
                    observerMethods = new ObserverMethodsBuilder(wbc, beanAnnotatedType).defineObserverMethods(bean);
                } else {
                    observerMethods = new HashSet<ObserverMethod<?>>();
                }

                Set<ProducerFieldBean<?>> producerFields =
                        ignoreProducer ? Collections.emptySet() : new ProducerFieldBeansBuilder(wbc, beanAnnotatedType).defineProducerFields(bean);
                Set<ProducerMethodBean<?>> producerMethods =
                        ignoreProducer ? Collections.emptySet() : new ProducerMethodBeansBuilder(wbc, beanAnnotatedType).defineProducerMethods(bean, producerFields);

                ManagedBean managedBean = (ManagedBean)bean;
                Map<ProducerMethodBean<?>,AnnotatedMethod<?>> annotatedMethods =
                        new HashMap<ProducerMethodBean<?>, AnnotatedMethod<?>>();

                if (!producerFields.isEmpty() || !producerMethods.isEmpty()) {
                    final Priority priority = annotatedType.getAnnotation(Priority.class);
                    if (priority != null && !wbc.getAlternativesManager()
                            .isAlternative(annotatedType.getJavaClass(), Collections.<Class<? extends Annotation>>emptySet())) {
                        wbc.getAlternativesManager().addPriorityClazzAlternative(annotatedType.getJavaClass(), priority);
                    }
                }

                for(ProducerMethodBean<?> producerMethod : producerMethods) {
                    AnnotatedMethod method = wbc.getAnnotatedElementFactory().newAnnotatedMethod(producerMethod.getCreatorMethod(), annotatedType);
                    wbc.getWebBeansUtil().inspectDeploymentErrorStack("There are errors that are added by ProcessProducer event observers for "
                            + "ProducerMethods. Look at logs for further details");

                    annotatedMethods.put(producerMethod, method);
                }

                Map<ProducerFieldBean<?>,AnnotatedField<?>> annotatedFields =
                        new HashMap<ProducerFieldBean<?>, AnnotatedField<?>>();

                for(ProducerFieldBean<?> producerField : producerFields) {
                    /* TODO: check if needed in HA
                    webBeansContext.getWebBeansUtil().inspectDeploymentErrorStack("There are errors that are added by ProcessProducer event observers for"
                            + " ProducerFields. Look at logs for further details");
                    */

                    annotatedFields.put(producerField,
                            wbc.getAnnotatedElementFactory().newAnnotatedField(
                                    producerField.getCreatorField(),
                                    wbc.getAnnotatedElementFactory().newAnnotatedType(producerField.getBeanClass())));
                }

                Map<ObserverMethod<?>,AnnotatedMethod<?>> observerMethodsMap =
                        new HashMap<ObserverMethod<?>, AnnotatedMethod<?>>();

                for(ObserverMethod<?> observerMethod : observerMethods) {
                    ObserverMethodImpl<?> impl = (ObserverMethodImpl<?>)observerMethod;
                    AnnotatedMethod<?> method = impl.getObserverMethod();

                    observerMethodsMap.put(observerMethod, method);
                }

                //Fires ProcessManagedBean
                ProcessBeanImpl processBeanEvent = new GProcessManagedBean(managedBean, annotatedType);
                beanManager.fireEvent(processBeanEvent, true);
                processBeanEvent.setStarted();
                wbc.getWebBeansUtil().inspectDefinitionErrorStack("There are errors that are added by ProcessManagedBean event observers for " +
                        "managed beans. Look at logs for further details");

                //Fires ProcessProducerMethod
                wbc.getWebBeansUtil().fireProcessProducerMethodBeanEvent(annotatedMethods, annotatedType);
                wbc.getWebBeansUtil().inspectDefinitionErrorStack("There are errors that are added by ProcessProducerMethod event observers for " +
                        "producer method beans. Look at logs for further details");

                //Fires ProcessProducerField
                wbc.getWebBeansUtil().fireProcessProducerFieldBeanEvent(annotatedFields);
                wbc.getWebBeansUtil().inspectDefinitionErrorStack("There are errors that are added by ProcessProducerField event observers for " +
                        "producer field beans. Look at logs for further details");

                //Fire ObservableMethods
                wbc.getWebBeansUtil().fireProcessObservableMethodBeanEvent(observerMethodsMap);
                wbc.getWebBeansUtil().inspectDefinitionErrorStack("There are errors that are added by ProcessObserverMethod event observers for " +
                        "observer methods. Look at logs for further details");
                if(!wbc.getWebBeansUtil().isAnnotatedTypeDecoratorOrInterceptor(annotatedType)) {
                    beanManager.addBean(bean);
                    for (ProducerMethodBean<?> producerMethod : producerMethods) {
                        // add them one after the other to enable serialization handling et al
                        beanManager.addBean(producerMethod);
                    }
                    for (ProducerFieldBean<?> producerField : producerFields) {
                        // add them one after the other to enable serialization handling et al
                        beanManager.addBean(producerField);
                    }
                }
            }
        }
    }


}
