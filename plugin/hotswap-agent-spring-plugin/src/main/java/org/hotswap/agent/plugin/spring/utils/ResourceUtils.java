/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.utils;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.spring.files.XmlBeanDefinitionScannerAgent;
import org.hotswap.agent.util.spring.util.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URL;

public class ResourceUtils {
    private static final AgentLogger LOGGER = AgentLogger.getLogger(XmlBeanDefinitionScannerAgent.class);
    public static String getPath(Resource resource) {
        if (resource == null) {
            return null;
        }
        String path;
        if (resource instanceof ClassPathResource) {
            path = ((ClassPathResource) resource).getPath();
        } else if (resource instanceof ByteArrayResource) {
            LOGGER.debug("Cannot get path from ByteArrayResource: {}", new String(((ByteArrayResource) resource).getByteArray()));
            return null;
        } else {
            try {
                path = convertToClasspathURL(resource.getURL().getPath());
            } catch (IOException e) {
                LOGGER.error("Cannot get url from resource: {}", e, resource);
                return null;
            }
        }
        return path;
    }

    /**
     * convert src/main/resources/xxx.xml and classes/xxx.xml to xxx.xml
     *
     * @param filePath the file path to convert
     * @return if convert succeed, return classpath path, or else return file path
     */
    public static String convertToClasspathURL(String filePath) {
        String[] paths = filePath.split("src/main/resources/");
        if (paths.length == 2) {
            return paths[1];
        }

        paths = filePath.split("WEB-INF/classes/");
        if (paths.length == 2) {
            return paths[1];
        }

        paths = filePath.split("WEB-INF/");
        if (paths.length == 2) {
            return paths[1];
        }

        paths = filePath.split("target/classes/");
        if (paths.length == 2) {
            return paths[1];
        }

        paths = filePath.split("target/test-classes/");
        if (paths.length == 2) {
            return paths[1];
        }

        LOGGER.error("failed to convert filePath {} to classPath path", filePath);
        return filePath;
    }

    /**
     * convert src/main/resources/xxx.xml and classes/xxx.xml to xxx.xml
     *
     * @param extraClassPaths the extra class paths
     * @param filePath        the file path to convert
     * @return if convert succeed, return classpath path, or else return file path
     */
    public static String convertToClasspathURL(URL[] extraClassPaths, String filePath) {
        String path = convertToClasspathURL(filePath);
        if (!StringUtils.isEmpty(path)) {
            return path;
        }
        if (extraClassPaths != null && extraClassPaths.length != 0) {
            for (URL extraClassPath : extraClassPaths) {
                String extraClassPathStr = extraClassPath.getPath();
                String[] paths = filePath.split(extraClassPathStr);
                if (paths.length == 2) {
                    return paths[1];
                }
            }
        }

        LOGGER.error("failed to convert filePath {} to classPath path", filePath);
        return filePath;
    }
}
