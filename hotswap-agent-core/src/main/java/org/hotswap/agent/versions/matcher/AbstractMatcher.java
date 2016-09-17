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
