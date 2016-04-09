package org.hotswap.agent.plugin.hibernate3.session.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.impl.SessionFactoryImpl;
import org.hotswap.agent.javassist.util.proxy.MethodHandler;
import org.hotswap.agent.javassist.util.proxy.Proxy;
import org.hotswap.agent.javassist.util.proxy.ProxyFactory;

/**
 * For Hibernate without EJB (EntityManager).
 * <p/>
 * TODO - Not tested, some additional Configuration cleanup may be necessary
 *
 * @author Jiri Bubnik
 * @author alpapad@gmail.com
 */
@SuppressWarnings("restriction")
public class SessionFactoryProxy {
	private static Map<Configuration, SessionFactoryProxy> proxiedFactories = new HashMap<Configuration, SessionFactoryProxy>();

	public static SessionFactoryProxy getWrapper(Configuration configuration) {
		synchronized (proxiedFactories) {
			if (!proxiedFactories.containsKey(configuration)) {
				proxiedFactories.put(configuration, new SessionFactoryProxy(configuration));
			}
			return proxiedFactories.get(configuration);
		}
	}

	public static void refreshProxiedFactories() {
		synchronized (proxiedFactories) {
			for (SessionFactoryProxy wrapper : proxiedFactories.values()){
				try {
					wrapper.refreshProxiedFactory();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private Configuration configuration;

	private volatile SessionFactory currentInstance;
	
	private SessionFactoryProxy(Configuration configuration) {
		this.configuration = configuration;
	}

	public void refreshProxiedFactory() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		ReInitializable r = ReInitializable.class.cast(configuration);
		r.hotSwap();
		currentInstance = r._buildSessionFactory();
	}

	public SessionFactory proxy(SessionFactory sessionFactory) {
		try {
			this.currentInstance = sessionFactory;

			ProxyFactory factory = new ProxyFactory();
			factory.setSuperclass(SessionFactoryImpl.class);
			factory.setInterfaces(new Class[] { SessionFactory.class, SessionFactoryImplementor.class });

			MethodHandler handler = new MethodHandler() {
				@Override
				public Object invoke(Object self, Method overridden, Method forwarder, Object[] args) throws Throwable {
					return overridden.invoke(currentInstance, args);
				}
			};

			Object instance;
			try {
				Constructor<?> constructor = sun.reflect.ReflectionFactory.getReflectionFactory().newConstructorForSerialization(factory.createClass(), Object.class.getDeclaredConstructor(new Class[0]));
				instance = constructor.newInstance();
				((Proxy) instance).setHandler(handler);
			} catch (Exception e) {
				e.printStackTrace();
				throw new Error("Unable instantiate SessionFactory proxy", e);
			}

			return (SessionFactory) instance;
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Unable instantiate SessionFactory proxy", e);
		}
	}
}
