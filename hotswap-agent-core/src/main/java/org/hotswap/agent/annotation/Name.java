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
package org.hotswap.agent.annotation;

/**
 *  Defines a manifest attribute-value pair to match.
 *
 * @author alpapad@gmail.com
 */
public @interface Name {

    /**
     * The manifest entry key.
     *
     * @return the string
     */
    String key();

    /**
     * The entry value to be matched.
     *
     * @return the string
     */
    String value();

    /** The Bundle version. */
    // Bundle-Version: 2.2.9
    static String BundleVersion = "Bundle-Version";


    /**
     * <p>
     * The only required header for OSGI bundles, this entry specifies a unique identifier for a
     * bundle, based on the reverse domain name convention (used also by the
     * java packages).
     * </p>
     * <code>Bundle-SymbolicName: javax.servlet-api</code>
     */
    static String BundleSymbolicName = "Bundle-SymbolicName";

    /**
     * <p>
     * Defines a human-readable name, without spaces, for this bundle. Setting
     * this header is recommend since it can provide a shorter, more meaningful
     * information about the bundle content then Bundle-SymbolicName.
     * </p>
     * <code>Bundle-Name: Java Servlet API</code>
     */
    static String BundleName = "Bundle-Name";


    /**
     * The value is a string that defines the version of the extension
     * implementation.
     */
    static String ImplementationVersion = "Implementation-Version";

    /**
     * The value is a string that defines the title of the extension
     * implementation.
     */
    static String ImplementationTitle = "Implementation-Title";// :

    /**
     * The value is a string that defines the organization that maintains the
     * extension implementation.
     */
    static String ImplementationVendor = "Implementation-Vendor";//

    /**
     * The value is a string id that uniquely defines the organization that
     * maintains the extension implementation.;
     */
    static String ImplementationVendorId = "Implementation-Vendor-Id";

    /** This attribute defines the URL from which the extension implementation can be downloaded from. */
    static String ImplementationURL = "Implementation-URL";

    /** This attribute defines the URL from which the extension implementation can be downloaded from. */
    static String ImplementationUrl = "Implementation-Url";

    /**
     * The value is a string that defines the version of the extension
     * specification.
     */
    static String SpecificationVersion = "Specification-Version";

    /**
     * The value is a string that defines the title of the extension
     * specification.
     */
    static String SpecificationTitle = "Specification-Title";

    /**
     * The value is a string that defines the organization that maintains the
     * extension specification.
     */
    static String SpecificationVendor = "Specification-Vendor";
}
