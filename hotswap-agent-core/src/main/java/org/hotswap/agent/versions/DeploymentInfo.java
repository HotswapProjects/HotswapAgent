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
package org.hotswap.agent.versions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;

import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.spring.io.resource.Resource;
import org.hotswap.agent.util.spring.path.PathMatchingResourcePatternResolver;

/**
 * The DeploymentInfo collects all known information (jar maven and manifest
 * artifacts). The DeploymentInfo is usually retrieved via the class loader (see
 * static method
 * <code>DeploymentInfo.fromClassLoader(ClassLoader classloader)</code>)
 * 
 * @author alpapad@gmail.com
 */
public class DeploymentInfo {

    /** The logger. */
    private static AgentLogger LOGGER = AgentLogger.getLogger(DeploymentInfo.class);
    
    /** The set of maven coordinates this deployment depends on. */
    private Set<MavenInfo> maven = new LinkedHashSet<>();

    /** The set of manifest attributes this deployment depends on. */
    private Set<ManifestInfo> manifest;

    /**
     * Instantiates a new deployment info.
     *
     * @param maven
     *            the maven coordinates
     * @param manifest
     *            the manifest attributes
     */
    public DeploymentInfo(Set<MavenInfo> maven, Set<ManifestInfo> manifest) {
        this.maven = maven;
        this.manifest = manifest;
    }

    /**
     * Gets the manifest attributes.
     *
     * @return the manifest attributes
     */
    public Set<ManifestInfo> getManifest() {
        return manifest;
    }

    /**
     * Gets the maven coordinates.
     *
     * @return the maven coordinates
     */
    public Set<MavenInfo> getMaven() {
        return maven;
    }

    /**
     * Sets the maven coordinates.
     *
     * @param maven
     *            the new maven coordinates
     */
    public void setMaven(Set<MavenInfo> maven) {
        this.maven = maven;
    }

    /**
     * Checks if is empty.
     *
     * @return true, if is empty
     */
    public boolean isEmpty() {
        return maven == null || maven.size() == 0 || manifest == null || manifest.isEmpty();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DeploymentInfo other = (DeploymentInfo) obj;
        if (manifest == null) {
            if (other.manifest != null) {
                return false;
            }
        } else if (!manifest.equals(other.manifest)) {
            return false;
        }
        if (maven == null) {
            if (other.maven != null) {
                return false;
            }
        } else if (!maven.equals(other.maven)) {
            return false;
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((manifest == null) ? 0 : manifest.hashCode());
        result = prime * result + ((maven == null) ? 0 : maven.hashCode());
        return result;
    }

    /**
     * Sets the manifest.
     *
     * @param manifest
     *            the new manifest
     */
    public void setManifest(Set<ManifestInfo> manifest) {
        this.manifest = manifest;
    }

    /**
     * Load the deployment info for this classloader.
     *
     * @param classloader
     *            the ClassLoader
     * @return the deployment info
     */
    public static DeploymentInfo fromClassLoader(ClassLoader classloader) {
        ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(classloader);

            Set<MavenInfo> maven = new LinkedHashSet<>();
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classloader);

            try {
                Enumeration<URL> urls = classloader.getResources("META-INF/maven/");
                while (urls.hasMoreElements()) {
                    URL u = urls.nextElement();
                    try {
                        Resource[] resources = resolver.getResources(u.toExternalForm() + "**/pom.properties");
                        if (resources != null) {
                            if(LOGGER.isDebugEnabled()){
                                LOGGER.debug("META-INF/maven/**/pom.properties FOUND:{}", Arrays.toString(resources));
                            }
                            for (Resource resource : resources) {
                                MavenInfo m = getMavenInfo(resource);
                                if (m != null) {
                                    maven.add(m);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                LOGGER.debug("Error trying to find maven properties", e);
            }

            return new DeploymentInfo(maven, getManifest(classloader));
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassLoader);
        }
    }

    /**
     * Gets the maven info.
     *
     * @param resource
     *            the resource
     * @return the maven info
     */
    private static MavenInfo getMavenInfo(Resource resource) {
        if(LOGGER.isDebugEnabled()){
            LOGGER.debug("RESOURCE_MAVEN:" + resource.getClass() + "-->" + resource.getDescription() + "----" + resource.getFilename());
        }
        try (InputStream is = resource.getInputStream()) {
            Properties p = new Properties();
            p.load(is);
            // String version, String name, String vendor
            return new MavenInfo(p.getProperty("groupId"), p.getProperty("artifactId"), p.getProperty("version"));
        } catch (IOException e) {
            LOGGER.debug("Error trying to read maven properties", e);
        }
        return null;
    }

    /**
     * Gets the manifest Info.
     *
     * @param classloader
     *            the ClassLoader
     * @return the manifest
     */
    public static Set<ManifestInfo> getManifest(ClassLoader classloader) {

        Set<ManifestInfo> manifests = new LinkedHashSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classloader);

        try {
            Enumeration<URL> urls = classloader.getResources("META-INF/MANIFEST.MF");
            while (urls.hasMoreElements()) {
                URL u = urls.nextElement();
                try {
                    Resource[] resources = resolver.getResources(u.toExternalForm());
                    if (resources != null) {
                        if(LOGGER.isDebugEnabled()){
                            LOGGER.debug("META-INF/MANIFEST.MF FOUND:\n" + Arrays.toString(resources));
                        }
                        for (Resource resource : resources) {
                            ManifestInfo m = getManifest(resource);
                            if (m != null) {
                                manifests.add(m);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Error trying to get manifest entries", e);
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Error trying to get manifest entries", e);
        }
        return manifests;
    }

    /**
     * Gets the manifest for a specific resource.
     *
     * @param resource
     *            the resource
     * @return the ManifestInfo
     */
    public static ManifestInfo getManifest(Resource resource) {
        if(LOGGER.isDebugEnabled()){
            LOGGER.debug("RESOURCE_MANIFEST:" + resource.getClass() + "-->" + resource.getDescription() + "----" + resource.getFilename());
        }
        try (InputStream is = resource.getInputStream()) {
            Manifest man = new Manifest(is);
            if (man != null) {
                return new ManifestInfo(man);
            }
        } catch (IOException e) {
            LOGGER.debug("Error trying to read manifest", e);
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "DeploymentInfo [maven=" + maven + ", manifest=" + manifest + "]";
    }
}
