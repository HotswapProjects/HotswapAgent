package org.hotswap.agent.plugin.spring;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SpringGlobalCaches {
    // keep the bean name and xml file relation for the beans which are defined in xml file and the bean contains placeholder
    Map<String, String> placeHolderXmlRelation = new ConcurrentHashMap<>();
}
