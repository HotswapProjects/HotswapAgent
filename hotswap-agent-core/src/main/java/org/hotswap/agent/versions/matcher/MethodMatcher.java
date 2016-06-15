/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hotswap.agent.versions.matcher;

import java.lang.reflect.Method;

import org.hotswap.agent.annotation.Versions;

/**
 * The MethodMatcher is the matcher responsible for parsing and applying the
 * matching algorithm at the method level. Each method in the plugin is allowed
 * to match different versions of artifacts, so one plugin could potentially
 * work for multiple versions of the same artifact which might have different
 * implementation details.
 * 
 * @author alpapad@gmail.com
 */
public class MethodMatcher extends AbstractMatcher {

    /**
     * Instantiates a new method matcher.
     *
     * @param method
     *            the method
     */
    public MethodMatcher(Method method) {
        super(method.getAnnotation(Versions.class));
    }
}
