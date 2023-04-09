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
package org.hotswap.agent.versions.matcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes.Name;

import org.hotswap.agent.annotation.Manifest;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.spring.util.PatternMatchUtils;
import org.hotswap.agent.util.spring.util.StringUtils;
import org.hotswap.agent.versions.DeploymentInfo;
import org.hotswap.agent.versions.ArtifactVersion;
import org.hotswap.agent.versions.InvalidVersionSpecificationException;
import org.hotswap.agent.versions.ManifestInfo;
import org.hotswap.agent.versions.VersionMatchResult;
import org.hotswap.agent.versions.VersionMatcher;
import org.hotswap.agent.versions.VersionRange;

/**
 * The ManifestMatcher will parse and match a single @Manifest definition 
 * 
 * @author alpapad@gmail.com
 */
public class ManifestMatcher implements VersionMatcher {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ManifestMatcher.class);

    /** The included versions range */
    private final VersionRange includes;
    
    /** The excluded versions range */
    private final VersionRange excludes;
    
    /** The properties. */
    private final Map<Name, String> properties;

    /** The includes string. */
    private final String includesString;
    
    /** The excludes string. */
    private final String excludesString;

    /** The version. */
    private final Name[] version;

    /**
     * Instantiates a new manifest matcher.
     *
     * @param cfg the cfg
     * @throws InvalidVersionSpecificationException the invalid version specification exception
     */
    public ManifestMatcher(Manifest cfg) throws InvalidVersionSpecificationException {
        if (StringUtils.hasText(cfg.value())) {
            this.includesString = cfg.value().trim();
            this.includes = VersionRange.createFromVersionSpec(includesString);
        } else {
            this.includes = null;
            this.includesString = null;
        }

        if (StringUtils.hasText(cfg.excludeVersion())) {
            this.excludesString = cfg.excludeVersion().trim();
            this.excludes = VersionRange.createFromVersionSpec(excludesString);
        } else {
            this.excludes = null;
            this.excludesString = null;
        }
        if(cfg.versionName() == null || cfg.versionName().length == 0) {
            version = null;
        } else {
            List<Name >versions = new ArrayList<>();
            for(String versionName: cfg.versionName()) {
                if (StringUtils.hasText(versionName)) {
                    versions.add(new Name(versionName));
                }
            }
            version = versions.toArray(new Name[versions.size()]);
        }
        
        if (cfg.names() != null && cfg.names().length > 0) {
            this.properties = new HashMap<>();
            for (org.hotswap.agent.annotation.Name name : cfg.names()) {
                if(StringUtils.hasText(name.key()) && StringUtils.hasText(name.value())) {
                    this.properties.put(new Name(name.key()), name.value());
                }
            }
        } else {
            this.properties = Collections.emptyMap();
        }
    }

    /**
     * Gets the included versions range
     *
     * @return the included versions range
     */
    public VersionRange getIncludes() {
        return includes;
    }

    /**
     * Gets the excluded versions range
     *
     * @return the excluded versions range
     */
    public VersionRange getExcludes() {
        return excludes;
    }

    /**
     * Gets the properties.
     *
     * @return the properties
     */
    public Map<Name, String> getProperties() {
        return properties;
    }

    /* (non-Javadoc)
     * @see org.hotswap.agent.config.ArtifactMatcher#matches(org.hotswap.agent.versions.DeploymentInfo)
     */
    public VersionMatchResult matches(DeploymentInfo info) {
        // Skip if no manifest configuration
        if(info.getManifest() == null  || info.getManifest().size() == 0) {
            return VersionMatchResult.SKIPPED;
        }
        
       	for (ManifestInfo manifest: info.getManifest()) {
       	    VersionMatchResult result = match(manifest);
       	 
       		if(VersionMatchResult.MATCHED.equals(result)){
       		    LOGGER.debug("Matched {} with {}", this, manifest);
       			return VersionMatchResult.MATCHED;
       		}
            if(VersionMatchResult.REJECTED.equals(result)){
                LOGGER.debug("Rejected {} with {}", this, manifest);
                return VersionMatchResult.REJECTED;
            }
       	}
       	
       	// There were no matches (maybe another matcher will pass)
       	return VersionMatchResult.SKIPPED;
    }

    /**
     * Match.
     *
     * @param manifest the manifest
     * @return the version match result
     */
    private VersionMatchResult match(ManifestInfo manifest) {
    	if(manifest == null) {
    		return VersionMatchResult.SKIPPED;
    	}
        // We need a version...
        String artifactVersion = manifest.getValue(this.version);
        if(StringUtils.isEmpty(artifactVersion)){
            return VersionMatchResult.SKIPPED;
        }
        
        // if no properties, then skip
        if(properties.size() == 0) {
            return VersionMatchResult.SKIPPED;
        } else {
            for(Map.Entry<Name,String> e: properties.entrySet()) {
                String v = manifest.getValue(e.getKey());
                // ALL patterns MUST match, else skip
                if(!StringUtils.hasText(v) || !PatternMatchUtils.regexMatch(e.getValue(), v)) {
                    return VersionMatchResult.SKIPPED;
                }
            }
        }
        ArtifactVersion version = new ArtifactVersion(artifactVersion);
        
        if(excludes != null && excludes.containsVersion(version)) {
            return VersionMatchResult.REJECTED;
        }
        
        if(includes != null && !includes.containsVersion(version)) {
            return VersionMatchResult.REJECTED;
        } else {
            return VersionMatchResult.MATCHED;
        }
        
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ManifestMatcher [properties=" + properties + ", includes=" + includes + ", excludes=" + excludes + "]";
    }

    /* (non-Javadoc)
     * @see org.hotswap.agent.config.ArtifactMatcher#isApply()
     */
    @Override
    public boolean isApply() {
        return (StringUtils.hasText(includesString) || StringUtils.hasText(excludesString)) && (version != null);
    }

}