[ZK Framework](http://www.zkoss.org/)
====================================
* Change default value for library properties of ZK caches. If you want to return original defaults, override this
setting explicitly in zk.xml.
    * [org.zkoss.web.classWebResource.cache=false](http://books.zkoss.org/wiki/ZK_Configuration_Reference/zk.xml/The_Library_Properties/org.zkoss.web.classWebResource.cache)
    * [org.zkoss.zk.WPD.cache=false, org.zkoss.zk.WCS.cache=false](http://books.zkoss.org/wiki/ZK_Configuration_Reference/zk.xml/The_Library_Properties/org.zkoss.zk.WPD.cache)
    * [zk-dl.annotation.cache=false](https://code.google.com/p/zk-dl/source/browse/trunk/ZKComposer/src/main/resources/metainfo/zk/config.xml) -
       part of zk-dl extension ([http://zk.datalite.cz](http://zk.datalite.cz))
* Reload [ZK Labels](http://books.zkoss.org/wiki/ZK_Developer's_Reference/Internationalization/Labels) -
    Clear Labels cache after any .properties file change.
* ZUL properties resolver - clear org.zkoss.zel.BeanELResolver caches after any class change
* Method caches for Binding:
    * BindComposer - clear afterCompose method cache after any class change
    * BinderImpl - clear initMethodCache, commanMethodCache, globalCommandMethodCache after any class change


#### Implementation notes:
* Plugin initialization is triggered after DHtmlLayoutServlet.init() method in servlet classloader


## TODO:
* Support for [@Composite annotation](http://books.zkoss.org/wiki/ZK_Developer's_Reference/UI_Composing/Composite_Component#Define_Components_with_Java_Annotations)