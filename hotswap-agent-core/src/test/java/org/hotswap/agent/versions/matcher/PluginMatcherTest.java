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

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hotswap.agent.annotation.Manifest;
import org.hotswap.agent.annotation.Maven;
import org.hotswap.agent.annotation.Name;
import org.hotswap.agent.annotation.Versions;
import org.hotswap.agent.versions.DeploymentInfo;
import org.hotswap.agent.versions.ManifestInfo;
import org.hotswap.agent.versions.MavenInfo;
import org.hotswap.agent.versions.VersionMatchResult;
import org.junit.Test;

public class PluginMatcherTest {




    /**
2016-04-19 10:50:30,770 ERROR [stderr] (MSC service thread 1-3) META-INF/maven/* /pom.properties FOUND:
2016-04-19 10:50:30,770 ERROR [stderr] (MSC service thread 1-3) [class path resource [META-INF/maven/org.apache.myfaces.core/myfaces-impl/pom.properties], class path resource [META-INF/maven/org.apache.myfaces.core.internal/myfaces-impl-shared/pom.properties]]

2016-04-19 10:50:30,770 ERROR [stderr] (MSC service thread 1-3) RESOURCE:class org.hotswap.agent.util.spring.io.loader.DefaultResourceLoader$ClassPathContextResource-->class path resource [META-INF/maven/org.apache.myfaces.core/myfaces-impl/pom.properties]----pom.properties

2016-04-19 10:50:30,770 ERROR [stderr] (MSC service thread 1-3) RESOURCE:class org.hotswap.agent.util.spring.io.loader.DefaultResourceLoader$ClassPathContextResource-->class path resource [META-INF/maven/org.apache.myfaces.core.internal/myfaces-impl-shared/pom.properties]----pom.properties

2016-04-19 10:50:30,772 ERROR [stderr] (MSC service thread 1-3) ARTIFACT_INFO:ArtifactInfo [maven=[MavenInfo [groupId=org.apache.myfaces.core, artifactId=myfaces-impl, version=2.2.9], MavenInfo [groupId=org.apache.myfaces.core.internal, artifactId=myfaces-impl-shared, version=2.2.9]], manifest=ManifestInfo [java.util.jar.Manifest@4fe5d570]]

2016-04-19 10:50:30,773 ERROR [stderr] (MSC service thread 1-3) PLUGIN_INFO:FAILED ---> AbstractMatcher [matchers=[MavenMatcher [groupId=org.apache.myfaces.core, artifactId=myfaces-api, includes=VersionRange [recommendedVersion=null, restrictions=[[2.2,)]], excludes=null], MavenMatcher [groupId=org.apache.myfaces.core, artifactId=myfaces-impl, includes=VersionRange [recommendedVersion=null, restrictions=[[2.2,)]], excludes=null], ManifestMatcher [properties={Bundle-Version=jh}, includes=VersionRange [recommendedVersion=null, restrictions=[[2.2,)]], excludes=null]], shouldApply=true]

201
     * @throws IOException
     */
    @Test
    public void testMatches() throws IOException {
        Set<MavenInfo> maven = new HashSet<MavenInfo>();
        maven.add(new MavenInfo("org.apache.myfaces.core","myfaces-impl","2.2.9"));
        maven.add(new MavenInfo("org.apache.myfaces.core.internal","myfaces-impl","2.2.9"));
        maven.add(new MavenInfo("org.apache.myfaces.core","myfaces-api","2.2.9"));

        ManifestInfo manifest = new ManifestInfo(new java.util.jar.Manifest(this.getClass().getResourceAsStream("/org/hotswap/agent/versions/matcher/TEST.MF")));

        DeploymentInfo info = new DeploymentInfo(maven, Collections.singleton(manifest));
        System.err.println(info);
        PluginMatcher p = new PluginMatcher(MatchingPlugin.class);
        System.err.println(p);
        assertEquals("Matching",VersionMatchResult.MATCHED, p.matches(info));
    }


    @Test
    public void testFails() throws IOException {
        Set<MavenInfo> maven = new HashSet<MavenInfo>();
        maven.add(new MavenInfo("org.apache.myfaces.core","myfaces-impl","2.2.9"));
        maven.add(new MavenInfo("org.apache.myfaces.core.internal","myfaces-impl","2.2.9"));
        maven.add(new MavenInfo("org.apache.myfaces.core","myfaces-api","2.2.9"));

        ManifestInfo manifest = new ManifestInfo(new java.util.jar.Manifest(this.getClass().getResourceAsStream("/org/hotswap/agent/versions/matcher/TEST.MF")));

        DeploymentInfo info = new DeploymentInfo(maven, Collections.singleton(manifest));
        System.err.println(info);


        PluginMatcher p = new PluginMatcher(NotMatchingPlugin.class);
        assertEquals("Not Matching",VersionMatchResult.REJECTED, p.matches(info));

    }

    @Test
    public void testFailedEmptyArtifactInfo() throws IOException {
        Set<MavenInfo> maven = new HashSet<MavenInfo>();
        ManifestInfo manifest = new ManifestInfo(null);
        DeploymentInfo info = new DeploymentInfo(maven, Collections.singleton(manifest));
        System.err.println(info);
        PluginMatcher p = new PluginMatcher(NotMatchingPlugin.class);
        assertEquals("Failed Matching",VersionMatchResult.REJECTED, p.matches(info));
    }

    @Test
    public void testSkippedEmpty2() throws IOException {
        Set<MavenInfo> maven = new HashSet<MavenInfo>();

        ManifestInfo manifest = new ManifestInfo(null);

        DeploymentInfo info = new DeploymentInfo(maven, Collections.singleton(manifest));
        System.err.println(info);


        PluginMatcher p = new PluginMatcher(ManifestMatcherTest.class);

        assertEquals("Skipped Matching",VersionMatchResult.SKIPPED, p.matches(info));
    }


    @Test
    public void testSkipped() throws IOException {
        Set<MavenInfo> maven = new HashSet<MavenInfo>();
        maven.add(new MavenInfo("org.apache.myfaces.core","myfaces-impl","2.2.9"));
        maven.add(new MavenInfo("org.apache.myfaces.core.internal","myfaces-impl","2.2.9"));
        maven.add(new MavenInfo("org.apache.myfaces.core","myfaces-api","2.2.9"));

        ManifestInfo manifest = new ManifestInfo(new java.util.jar.Manifest(this.getClass().getResourceAsStream("/org/hotswap/agent/versions/matcher/TEST.MF")));

        DeploymentInfo info = new DeploymentInfo(maven, Collections.singleton(manifest));
        System.err.println(info);


        PluginMatcher p = new PluginMatcher(ManifestMatcherTest.class);

        assertEquals("Skipped Matching",VersionMatchResult.SKIPPED, p.matches(info));
    }

    @Versions(//
            maven = { //
                    @Maven(value = "[2.2,)", artifactId = "myfaces-api", groupId = "org.apache.myfaces.core"), //
                    @Maven(value = "[2.2,)", artifactId = "myfaces-impl", groupId = "org.apache.myfaces.core")//
            }, //
            manifest = { //
                    @Manifest(names = { @Name(key = Name.BundleVersion, value = "jh") }, value = "[2.2,)")//
            })
    private static class MatchingPlugin {

    }


    @Versions(//
            maven = { //
                    @Maven(value = "[3.2,)", artifactId = "myfaces-api", groupId = "org.apache.myfaces.core"), //
                    @Maven(value = "[3.2,)", artifactId = "myfaces-impl", groupId = "org.apache.myfaces.core")//
            }, //
            manifest = { //
                    @Manifest(names = { @Name(key = Name.BundleVersion, value = "jh") }, value = "[2.2,)")//
            })
    private static class NotMatchingPlugin {

    }
}
