/**
 * 
 */
package org.hotswap.agent.plugin.myfaces;

/**
 * @author sinan.yumak
 *
 */
public class MyFacesConstants {
    
    private MyFacesConstants() {
        // prevent instantiation..
    }

    public static final String MANAGED_BEAN_ANNOTATION = "javax.faces.bean.ManagedBean";
    
    
    public static final String MANAGED_BEAN_RESOLVER_CLASS = "org.apache.myfaces.el.unified.resolver.ManagedBeanResolver";

    public static final String LIFECYCLE_IMPL_CLASS = "org.apache.myfaces.lifecycle.LifecycleImpl";
    
    
}
