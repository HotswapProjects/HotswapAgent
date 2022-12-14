/*
 * Copyright 2013-2022 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.util.classpool;

import org.hotswap.agent.util.ClassName;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.NotFoundException;

public class ClassPoolHelper {

    public static Map<String, Boolean> map = new ConcurrentHashMap<>();

    public static boolean hasBeenRead(ClassPool classPool, String className) {
        if (!map.containsKey(className)) {
            try {
                classPool.get(className);
                map.put(className, Boolean.TRUE);
                return true;
            } catch (NotFoundException e) {
                map.put(className, Boolean.FALSE);
            }
        }
        return map.get(className);
    }
    
    public static boolean hasBeenRead(ClassPool classPool, ClassName className) {
        return hasBeenRead(classPool, className.toString());
    }

}
