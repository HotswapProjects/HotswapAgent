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
import java.util.List;

import org.hotswap.agent.annotation.Manifest;
import org.hotswap.agent.annotation.Maven;
import org.hotswap.agent.annotation.Versions;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.versions.DeploymentInfo;
import org.hotswap.agent.versions.InvalidVersionSpecificationException;
import org.hotswap.agent.versions.VersionMatchResult;
import org.hotswap.agent.versions.VersionMatcher;

/**
 * The Class AbstractMatcher.
 * 
 * @author alpapad@gmail.com
 */
public class AbstractMatcher implements VersionMatcher{
	
	/** The logger. */
	protected AgentLogger LOGGER = AgentLogger.getLogger(getClass());
	
	/** The matchers. */
	protected final List<VersionMatcher> matchers = new ArrayList<>();
	
	/** The should apply. */
	protected boolean shouldApply = Boolean.FALSE;
	
	/**
	 * Instantiates a new abstract matcher.
	 *
	 * @param versions the versions
	 */
	public AbstractMatcher(Versions versions) {
		if(versions == null) {
		    return;
		}
		Maven[] maven = versions.maven();
		
		Manifest[] manifest = versions.manifest();
		
		if (maven != null) {
			for (Maven cfg : maven) {
				try {
					MavenMatcher m = new MavenMatcher(cfg);
					if (m.isApply()) {
						matchers.add(m);
						shouldApply = true;
					}
				} catch (InvalidVersionSpecificationException e) {
					LOGGER.error("Unable to parse Maven info for {}", e, cfg);
				}
			}
		}
		if (manifest != null) {
			for (Manifest cfg : manifest) {
				try {
					ManifestMatcher m = new ManifestMatcher(cfg);
					if (m.isApply()) {
						matchers.add(m);
						shouldApply = true;
					}
				} catch (InvalidVersionSpecificationException e) {
					LOGGER.error("Unable to parse Manifest info for {}", e, cfg);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.hotswap.agent.config.ArtifactMatcher#isApply()
	 */
	@Override
	public boolean isApply() {
		return shouldApply;
	}


	/* (non-Javadoc)
	 * @see org.hotswap.agent.config.ArtifactMatcher#matches(org.hotswap.agent.versions.DeploymentInfo)
	 */
	@Override
	public VersionMatchResult matches(DeploymentInfo info) {
		if (matchers.size() == 0) {
			return VersionMatchResult.SKIPPED;
		}
		for (VersionMatcher m : matchers) {
		    VersionMatchResult result = m.matches(info);
		    if(VersionMatchResult.MATCHED.equals(result)) {
		        LOGGER.debug("Matched:{}", m);
		        return VersionMatchResult.MATCHED;
		    }else if(VersionMatchResult.REJECTED.equals(result)) {
		        LOGGER.debug("Rejected:{}", m);
		        return VersionMatchResult.REJECTED;
		    }
		}
		// There were matchers, none succeeded
		LOGGER.debug("Rejected: Matchers existed, none matched!");
		return VersionMatchResult.REJECTED;
	}

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "AbstractMatcher [matchers=" + matchers + ", shouldApply=" + shouldApply + "]";
    }
}
