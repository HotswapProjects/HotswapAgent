package org.hotswap.agent.versions;


/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Describes a restriction in versioning.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class Restriction {
    
    /** The lower bound. */
    private final ArtifactVersion lowerBound;

    /** The lower bound inclusive. */
    private final boolean lowerBoundInclusive;

    /** The upper bound. */
    private final ArtifactVersion upperBound;

    /** The upper bound inclusive. */
    private final boolean upperBoundInclusive;

    /** The Constant EVERYTHING. */
    public static final Restriction EVERYTHING = new Restriction(null, false, null, false);

    /** The Constant NONE. */
    public static final Restriction NONE = new Restriction(new ArtifactVersion("0"), true, new ArtifactVersion(String.valueOf(Integer.MAX_VALUE)), true);

    /**
     * Instantiates a new restriction.
     *
     * @param lowerBound the lower bound
     * @param lowerBoundInclusive the lower bound inclusive
     * @param upperBound the upper bound
     * @param upperBoundInclusive the upper bound inclusive
     */
    public Restriction(ArtifactVersion lowerBound, boolean lowerBoundInclusive, ArtifactVersion upperBound, boolean upperBoundInclusive) {
        this.lowerBound = lowerBound;
        this.lowerBoundInclusive = lowerBoundInclusive;
        this.upperBound = upperBound;
        this.upperBoundInclusive = upperBoundInclusive;
    }

    /**
     * Gets the lower bound.
     *
     * @return the lower bound
     */
    public ArtifactVersion getLowerBound() {
        return lowerBound;
    }

    /**
     * Checks if is lower bound inclusive.
     *
     * @return true, if is lower bound inclusive
     */
    public boolean isLowerBoundInclusive() {
        return lowerBoundInclusive;
    }

    /**
     * Gets the upper bound.
     *
     * @return the upper bound
     */
    public ArtifactVersion getUpperBound() {
        return upperBound;
    }

    /**
     * Checks if is upper bound inclusive.
     *
     * @return true, if is upper bound inclusive
     */
    public boolean isUpperBoundInclusive() {
        return upperBoundInclusive;
    }

    /**
     * Contains version.
     *
     * @param version the version
     * @return true, if successful
     */
    public boolean containsVersion(ArtifactVersion version) {
        if (lowerBound != null) {
            int comparison = lowerBound.compareTo(version);

            if ((comparison == 0) && !lowerBoundInclusive) {
                return false;
            }
            if (comparison > 0) {
                return false;
            }
        }
        if (upperBound != null) {
            int comparison = upperBound.compareTo(version);

            if ((comparison == 0) && !upperBoundInclusive) {
                return false;
            }
            if (comparison < 0) {
                return false;
            }
        }

        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int result = 13;

        if (lowerBound == null) {
            result += 1;
        } else {
            result += lowerBound.hashCode();
        }

        result *= lowerBoundInclusive ? 1 : 2;

        if (upperBound == null) {
            result -= 3;
        } else {
            result -= upperBound.hashCode();
        }

        result *= upperBoundInclusive ? 2 : 3;

        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Restriction)) {
            return false;
        }

        Restriction restriction = (Restriction) other;
        if (lowerBound != null) {
            if (!lowerBound.equals(restriction.lowerBound)) {
                return false;
            }
        } else if (restriction.lowerBound != null) {
            return false;
        }

        if (lowerBoundInclusive != restriction.lowerBoundInclusive) {
            return false;
        }

        if (upperBound != null) {
            if (!upperBound.equals(restriction.upperBound)) {
                return false;
            }
        } else if (restriction.upperBound != null) {
            return false;
        }

        return upperBoundInclusive == restriction.upperBoundInclusive;

    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append(isLowerBoundInclusive() ? "[" : "(");
        if (getLowerBound() != null) {
            buf.append(getLowerBound().toString());
        }
        buf.append(",");
        if (getUpperBound() != null) {
            buf.append(getUpperBound().toString());
        }
        buf.append(isUpperBoundInclusive() ? "]" : ")");

        return buf.toString();
    }
}
