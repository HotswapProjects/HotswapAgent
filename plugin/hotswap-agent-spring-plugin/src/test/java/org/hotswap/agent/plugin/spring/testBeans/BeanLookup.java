package org.hotswap.agent.plugin.spring.testBeans;

//import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Service;

// @Service
public abstract class BeanLookup {
//	@Lookup
	public abstract BeanPrototype getBeanPrototype();
}
