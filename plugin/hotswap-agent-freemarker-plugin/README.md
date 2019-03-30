[Apache FreeMarker](https://freemarker.apache.org/) plugin
=================
Clear the class introspection cache when classes are redefined


#### Implementation notes
* Plugin initialization is triggered after FreeMarkerServer.init() in servlet classloader
* FreeMarker 2.3.28 has a typo in method 'clearClassIntrospecitonCache', used method name as-is
* Any class redefinition triggers the introspection cache to be cleared. events within 500ms are merged

## TODO
* Improve performance by only removing the changed classes from the cache. Code snippet:
    ```
    Object classIntrospector = ReflectionHelper.get(objectWrapper, "classIntrospector");
    Object cacheClassNames = ReflectionHelper.get(classIntrospector, "cacheClassNames");
    Boolean inCache = (Boolean)ReflectionHelper.invoke(cacheClassNames, cacheClassNames.getClass(), "contains",new Class[] {Object.class}, ctClass.getName());
    if(inCache) {
        ReflectionHelper.invoke(classIntrospector, classIntrospector.getClass(), "remove", new Class[] {Class.class}, appClassLoader.loadClass(ctClass.getName()));
    }
    ```
