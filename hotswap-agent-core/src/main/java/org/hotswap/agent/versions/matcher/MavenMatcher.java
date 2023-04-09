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

import org.hotswap.agent.annotation.Maven;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.spring.util.PatternMatchUtils;
import org.hotswap.agent.util.spring.util.StringUtils;
import org.hotswap.agent.versions.DeploymentInfo;
import org.hotswap.agent.versions.InvalidVersionSpecificationException;
import org.hotswap.agent.versions.MavenInfo;
import org.hotswap.agent.versions.VersionMatchResult;
import org.hotswap.agent.versions.VersionMatcher;
import org.hotswap.agent.versions.VersionRange;

/**
 * The MavenMatcher will parse and match a single @Mave definition 
 * 
 * @author alpapad@gmail.com
 */
public class MavenMatcher implements VersionMatcher {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MavenMatcher.class);

	/** The included versions. */
	private final VersionRange includes;
	
	/** The excluded versions. */
	private final VersionRange excludes;

	/** The artifact id. */
	private final String artifactId;

	/** The group id. */
	private final String groupId;

	/** The include versions as string. */
	private final String includesString;
	
	/** The exclude versions as string. */
	private final String excludesString;

	/**
	 * Instantiates a new maven matcher.
	 *
	 * @param cfg the Maven annotation 
	 * @throws InvalidVersionSpecificationException the invalid version specification exception
	 */
	public MavenMatcher(Maven cfg) throws InvalidVersionSpecificationException {
        this.artifactId = cfg.artifactId();
        this.groupId = cfg.groupId();
        if(StringUtils.hasText(cfg.value())) {
        	 this.includesString = cfg.value().trim();
        	 this.includes = VersionRange.createFromVersionSpec(includesString);
        } else {
        	this.includes = null;
        	this.includesString = null;
        }
        
        if(StringUtils.hasText(cfg.excludeVersion())){
        	this.excludesString = cfg.excludeVersion().trim();
        	this.excludes = VersionRange.createFromVersionSpec(excludesString);
        } else {
        	this.excludes =  null;
        	this.excludesString = null;
        }
    }

	/**
	 * Gets the included versions range.
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
	 * Gets the artifact id.
	 *
	 * @return the artifact id
	 */
	public String getArtifactId() {
		return artifactId;
	}

	/**
	 * Gets the group id.
	 *
	 * @return the group id
	 */
	public String getGroupId() {
		return groupId;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "MavenMatcher [groupId=" + groupId + ", artifactId=" + artifactId + ", includes=" + includes
				+ ", excludes=" + excludes + "]";
	}

	/* (non-Javadoc)
	 * @see org.hotswap.agent.config.ArtifactMatcher#matches(org.hotswap.agent.versions.DeploymentInfo)
	 */
	@Override
	public VersionMatchResult matches(DeploymentInfo info) {
        if(info.getMaven() == null || info.getMaven().size() == 0) {
            return VersionMatchResult.SKIPPED;
        }
        
        // A jar can carry multiple maven properties.

		for (MavenInfo mi : info.getMaven()) {
			if (PatternMatchUtils.regexMatch(groupId, mi.getGroupId()) && PatternMatchUtils.regexMatch(artifactId, mi.getArtifactId())) {
				
				if ((includes == null || includes.containsVersion(mi.getVersion())) && (excludes ==null || !excludes.containsVersion(mi.getVersion()))) {
				    LOGGER.debug("Matched {} with {}", this, mi);
				    return VersionMatchResult.MATCHED;
				}

				// If it is explicitly excluded, then false!
				if (excludes !=null && excludes.containsVersion(mi.getVersion())) {
				    LOGGER.debug("Rejected {} with {}", this, mi);
					return VersionMatchResult.REJECTED;
				}
			}
		}
			
		// There were no matches (maybe another matcher will pass)
		return VersionMatchResult.SKIPPED;
	}

	/* (non-Javadoc)
	 * @see org.hotswap.agent.config.ArtifactMatcher#isApply()
	 */
	@Override
	public boolean isApply() {
		return (StringUtils.hasText(artifactId) && StringUtils.hasText(groupId)) && (StringUtils.hasText(includesString) || StringUtils.hasText(excludesString));
	}
}