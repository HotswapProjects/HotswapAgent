/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.proxy.hscglib;

import org.hotswap.agent.util.ReflectionHelper;

/**
 * Parameters for new Cglib proxy creation
 *
 * @author Erki Ehtla
 *
 */
public class GeneratorParams {
    private Object generator;
    private Object param;

    public GeneratorParams(Object generator, Object param) {
        this.generator = generator;
        this.param = param;
    }

    public Object getGenerator() {
        return generator;
    }

    public void setGenerator(Object generator) {
        this.generator = generator;
    }

    public Object getParam() {
        return param;
    }

    public void setParam(Object params) {
        this.param = params;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((generator == null) ? 0 : generator.hashCode());
        result = prime * result + ((param == null) ? 0 : param.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GeneratorParams other = (GeneratorParams) obj;
        if (generator == null) {
            if (other.generator != null)
                return false;
        } else if (!generator.equals(other.generator))
            return false;
        if (param == null) {
            if (other.param != null)
                return false;
        } else if (!param.equals(other.param))
            return false;
        return true;
    }

    /**
     * Return an instance in this classloader
     *
     * @param paramsFromOtherClassLoader
     *            instamce in another classlaoder
     * @return instance in this classloader
     * @throws Exception
     */
    public static GeneratorParams valueOf(Object paramsFromOtherClassLoader)
            throws Exception {
        if (paramsFromOtherClassLoader.getClass()
                .getClassLoader() == GeneratorParams.class.getClassLoader()) {
            return (GeneratorParams) paramsFromOtherClassLoader;
        }
        Object params = ReflectionHelper.get(paramsFromOtherClassLoader,
                "param");
        Object generator = ReflectionHelper.get(paramsFromOtherClassLoader,
                "generator");
        return new GeneratorParams(generator, params);
    }
}