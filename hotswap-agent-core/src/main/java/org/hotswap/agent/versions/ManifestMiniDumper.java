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

import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;


/**
 * The Class ManifestMiniDumper.
 *
 * @author alpapad@gmail.com
 */
public class ManifestMiniDumper {
    /**
     * <code>Name</code> object for <code>Extension-List</code> manifest
     * attribute used for declaring dependencies on installed extensions.
     *
     * @see <a href=
     *      "../../../../technotes/guides/extensions/spec.html#dependency">
     *      Installed extension dependency</a>
     */
    public static final Name EXTENSION_LIST = new Name("Extension-List");

    /**
     * <code>Name</code> object for <code>Extension-Name</code> manifest
     * attribute used for declaring dependencies on installed extensions.
     *
     * @see <a href=
     *      "../../../../technotes/guides/extensions/spec.html#dependency">
     *      Installed extension dependency</a>
     */
    public static final Name EXTENSION_NAME = new Name("Extension-Name");

    /**
     * <code>Name</code> object for <code>Implementation-Title</code> manifest
     * attribute used for package versioning.
     *
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name IMPLEMENTATION_TITLE = new Name("Implementation-Title");

    /**
     * <code>Name</code> object for <code>Implementation-Version</code> manifest
     * attribute used for package versioning.
     *
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name IMPLEMENTATION_VERSION = new Name("Implementation-Version");

    /**
     * <code>Name</code> object for <code>Implementation-Vendor</code> manifest
     * attribute used for package versioning.
     *
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name IMPLEMENTATION_VENDOR = new Name("Implementation-Vendor");

    /**
     * <code>Name</code> object for <code>Implementation-Vendor-Id</code>
     * manifest attribute used for package versioning. Extension mechanism will
     * be removed in a future release. Use class path instead.
     *
     * @see <a href=
     *      "../../../../technotes/guides/extensions/versioning.html#applet">
     *      Optional Package Versioning</a>
     */
    public static final Name IMPLEMENTATION_VENDOR_ID = new Name("Implementation-Vendor-Id");

    /**
     * <code>Name</code> object for <code>Specification-Version</code> manifest
     * attribute used for package versioning.
     *
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name SPECIFICATION_VERSION = new Name("Specification-Version");

    /**
     * <code>Name</code> object for <code>Specification-Vendor</code> manifest
     * attribute used for package versioning.
     *
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name SPECIFICATION_VENDOR = new Name("Specification-Vendor");

    /**
     * <code>Name</code> object for <code>Specification-Title</code> manifest
     * attribute used for package versioning.
     *
     * @see <a href=
     *      "../../../../technotes/guides/versioning/spec/versioning2.html#wp90779">
     *      Java Product Versioning Specification</a>
     */
    public static final Name SPECIFICATION_TITLE = new Name("Specification-Title");

    /** The Constant BUNDLE_SYMBOLIC_NAME. */
    // Bundle-SymbolicName: javax.servlet-api
    public static final Name BUNDLE_SYMBOLIC_NAME = new Name("Bundle-SymbolicName");

    /** The Constant BUNDLE_NAME. */
    // Bundle-Name: Java Servlet API
    public static final Name BUNDLE_NAME = new Name("Bundle-Name");

    /** The Constant BUNDLE_VERSION. */
    // Bundle-Version: 2.2.9
    public static final Name BUNDLE_VERSION = new Name("Bundle-Version");

    /** The Constant VERSIONS. */
    public static final Name[] VERSIONS = new Name[] { BUNDLE_VERSION, IMPLEMENTATION_VERSION, SPECIFICATION_VENDOR };

    /** The Constant PACKAGE. */
    public static final Name[] PACKAGE = new Name[] { BUNDLE_SYMBOLIC_NAME, IMPLEMENTATION_VENDOR_ID, SPECIFICATION_VENDOR };

    /** The Constant TITLE. */
    public static final Name[] TITLE = new Name[] { BUNDLE_NAME, IMPLEMENTATION_TITLE, SPECIFICATION_VENDOR };

    /**
     * Dump.
     *
     * @param attr the attr
     * @return the string
     */
    public static String dump(Attributes attr) {
        String version = getAttribute(attr, null, VERSIONS);
        String pack = getAttribute(attr, null, PACKAGE);
        String title = getAttribute(attr, null, TITLE);

        return "version=" + version + ", package=" + pack + ", title=" + title;
    }

    /**
     * Gets the attribute.
     *
     * @param main the main
     * @param attr the attr
     * @param names the names
     * @return the attribute
     */
    private static String getAttribute(Attributes main, Attributes attr, Name... names) {
        if (names == null) {
            return null;
        }

        if (main != null) {
            String value;
            for (Name name : names) {
                value = main.getValue(name);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        if (attr != null) {
            String value;
            for (Name name : names) {
                value = attr.getValue(name);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        return null;
    }
}
