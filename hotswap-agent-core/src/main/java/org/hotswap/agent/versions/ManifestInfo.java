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

import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

import org.hotswap.agent.util.spring.util.StringUtils;


/**
 * The Class ManifestInfo.
 *
 * @author alpapad@gmail.com
 */
public class ManifestInfo {

    /** The mf. */
    private final Manifest mf;

    /** The attr. */
    private final Attributes attr;

    /** The main. */
    private final Attributes main;

    private final Map<String, Attributes> entries;

    /**
     * Instantiates a new manifest info.
     *
     * @param mf the mf
     */
    public ManifestInfo(Manifest mf) {
        this.mf = mf;
        if (mf != null) {
            attr = mf.getAttributes("");
            main = mf.getMainAttributes();
            entries = mf.getEntries();
        } else {
            attr = null;
            main = null;
            entries = null;
        }
    }

    /**
     * Checks if is empty.
     *
     * @return true, if is empty
     */
    public boolean isEmpty() {
        return mf == null || ((attr == null || attr.size() == 0) && (main == null || main.size() == 0));
    }

    /**
     * Gets the value.
     *
     * @param name the name
     * @return the value
     */
    public String getValue(Name... name) {
        if (name == null || isEmpty()) {
            return null;
        }
        return getAttribute(attr, main, entries, name);
    }

    /**
     * Gets the value.
     *
     * @param path the path
     * @param name the name
     * @return the value
     */
    public String getValue(String path, Name... name) {
        if (name == null || isEmpty()) {
            return null;
        }
        return getAttribute(StringUtils.isEmpty(path) ? attr : mf.getAttributes(path), main, entries, name);
    }

    /**
     * Gets the attribute.
     *
     * @param attr the attr
     * @param main the main
     * @param names the names
     * @return the attribute
     */
    private static String getAttribute(Attributes attr, Attributes main, Map<String, Attributes> entries, Name... names) {
        if (names == null || names.length == 0) {
            return null;
        }

        String ret = getAttributeByName(main, names);

        if (ret != null) {
            return ret;
        }

        ret = getAttributeByName(attr, names);

        if (ret != null) {
            return ret;
        }

        if (entries != null) {
            for (Iterator<Map.Entry<String, Attributes>> it = entries.entrySet().iterator();it.hasNext();) {
                Map.Entry<String, Attributes> entry = it.next();
                ret = getAttributeByName(entry.getValue(), names);
                if (ret != null) {
                    return ret;
                }

            }
        }

        return null;
    }

    private static String getAttributeByName(Attributes attr, Name... names) {
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

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ManifestInfo other = (ManifestInfo) obj;
        if (mf == null) {
            if (other.mf != null) {
                return false;
            }
        } else if (!mf.equals(other.mf)) {
            return false;
        }
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mf == null) ? 0 : mf.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        if (mf != null) {
            return "ManifestInfo [" + ManifestMiniDumper.dump(mf.getMainAttributes()) + ", entries:" + mf.getEntries() + "]";
        } else {
            return "ManifestInfo [null]";
        }
    }

    // public static String dump(Attributes a) {
    // if(a == null) {
    // return "null";
    // }
    // StringBuilder sb = new StringBuilder();
    // for(Map.Entry<Object,Object> e: a.entrySet()){
    // sb.append("[").append(e.getKey()).append("=").append(e.getValue()).append("],");
    // }
    // return sb.toString();
    // }
}
