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

/**
 * The Interface VersionMatcher.
 *
 * VersionMatcher's are chained, multiple can be defined via annotations on a
 * plugin class or specific plugin methods.
 *
 * @author alpapad@gmail.com
 */
public interface VersionMatcher {

    /**
     * Return true if this matcher should be applied!.
     *
     * @return true, if is apply
     */
    boolean isApply();

    /**
     * Return a version match result. When an implementation is unable to decide
     * then a<code>VersionMatchResult.SKIPPED</code> should be returned so the
     * next one will have a chance to decide.
     *
     * @param info
     *            the info
     * @return the version match result
     */
    VersionMatchResult matches(DeploymentInfo info);
}