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

import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.NoSuchElementException;


/**
 * Default implementation of artifact versioning.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class ArtifactVersion implements Comparable<ArtifactVersion> {

    /** The version. */
    private final String version;

    /** The major version. */
    private Integer majorVersion;

    /** The minor version. */
    private Integer minorVersion;

    /** The incremental version. */
    private Integer incrementalVersion;

    /** The build number. */
    private Integer buildNumber;

    /** The qualifier. */
    private String qualifier;

    /** The comparable. */
    private ComparableVersion comparable;

    /**
     * Instantiates a new artifact version.
     *
     * @param version the version
     */
    public ArtifactVersion(String version) {
        this.version = version != null ? version.trim() : "";
        parseVersion(version);
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return 11 + comparable.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ArtifactVersion)) {
            return false;
        }

        return compareTo(ArtifactVersion.class.cast(other)) == 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(ArtifactVersion otherVersion) {
        return this.comparable.compareTo(otherVersion.comparable);
    }

    /**
     * Gets the major version.
     *
     * @return the major version
     */
    public int getMajorVersion() {
        return majorVersion != null ? majorVersion : 0;
    }

    /**
     * Gets the minor version.
     *
     * @return the minor version
     */
    public int getMinorVersion() {
        return minorVersion != null ? minorVersion : 0;
    }

    /**
     * Gets the incremental version.
     *
     * @return the incremental version
     */
    public int getIncrementalVersion() {
        return incrementalVersion != null ? incrementalVersion : 0;
    }

    /**
     * Gets the builds the number.
     *
     * @return the builds the number
     */
    public int getBuildNumber() {
        return buildNumber != null ? buildNumber : 0;
    }

    /**
     * Gets the qualifier.
     *
     * @return the qualifier
     */
    public String getQualifier() {
        return qualifier;
    }

    /**
     * Parses the version.
     *
     * @param version the version
     */
    public final void parseVersion(String version) {
        comparable = new ComparableVersion(version);

        int index = version.indexOf("-");

        String part1;
        String part2 = null;

        if (index < 0) {
            part1 = version;
        } else {
            part1 = version.substring(0, index);
            part2 = version.substring(index + 1);
        }

        if (part2 != null) {
            try {
                if ((part2.length() == 1) || !part2.startsWith("0")) {
                    buildNumber = Integer.valueOf(part2);
                } else {
                    qualifier = part2;
                }
            } catch (NumberFormatException e) {
                qualifier = part2;
            }
        }

        if ((!part1.contains(".")) && !part1.startsWith("0")) {
            try {
                majorVersion = Integer.valueOf(part1);
            } catch (NumberFormatException e) {
                // qualifier is the whole version, including "-"
                qualifier = version;
                buildNumber = null;
            }
        } else {
            boolean fallback = false;

            StringTokenizer tok = new StringTokenizer(part1, ".");
            try {
                majorVersion = getNextIntegerToken(tok);
                if (tok.hasMoreTokens()) {
                    minorVersion = getNextIntegerToken(tok);
                }
                if (tok.hasMoreTokens()) {
                    incrementalVersion = getNextIntegerToken(tok);
                }
                if (tok.hasMoreTokens()) {
                    qualifier = tok.nextToken();
                    fallback = Pattern.compile("\\d+").matcher(qualifier).matches();
                }

                // string tokenzier won't detect these and ignores them
                if (part1.contains("..") || part1.startsWith(".") || part1.endsWith(".")) {
                    fallback = true;
                }
            } catch (NumberFormatException e) {
                fallback = true;
            }

            if (fallback) {
                // qualifier is the whole version, including "-"
                qualifier = version;
                majorVersion = null;
                minorVersion = null;
                incrementalVersion = null;
                buildNumber = null;
            }
        }
    }

    /**
     * Gets the next integer token.
     *
     * @param tok the tok
     * @return the next integer token
     */
    private static Integer getNextIntegerToken(StringTokenizer tok) {
        try {
            String s = tok.nextToken();
            if ((s.length() > 1) && s.startsWith("0")) {
                throw new NumberFormatException("Number part has a leading 0: '" + s + "'");
            }
            return Integer.valueOf(s);
        } catch (NoSuchElementException e) {
            throw new NumberFormatException("Number is invalid");
        }
    }

    /**
     * Dump.
     *
     * @return the string
     */
    public String dump() {
        return "ArtifactVersion [version=" + version + ", majorVersion=" + majorVersion + ", minorVersion=" + minorVersion + ", incrementalVersion=" + incrementalVersion + ", buildNumber=" + buildNumber + ", qualifier=" + qualifier + ", comparable=" + comparable + "]";
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return comparable.toString();
    }
}
