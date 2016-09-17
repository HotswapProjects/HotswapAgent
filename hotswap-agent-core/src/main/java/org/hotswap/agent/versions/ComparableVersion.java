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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Stack;


/**
 * Generic implementation of version comparison.
 * 
 * <p>
 * Features:
 * <ul>
 * <li>mixing of '<code>-</code>' (dash) and '<code>.</code>' (dot) separators,
 * </li>
 * <li>transition between characters and digits also constitutes a separator:
 * <code>1.0alpha1 =&gt; [1, 0, alpha, 1]</code></li>
 * <li>unlimited number of version components,</li>
 * <li>version components in the text can be digits or strings,</li>
 * <li>strings are checked for well-known qualifiers and the qualifier ordering
 * is used for version ordering. Well-known qualifiers (case insensitive) are:
 * <ul>
 * <li><code>alpha</code> or <code>a</code></li>
 * <li><code>beta</code> or <code>b</code></li>
 * <li><code>milestone</code> or <code>m</code></li>
 * <li><code>rc</code> or <code>cr</code></li>
 * <li><code>snapshot</code></li>
 * <li><code>(the empty string)</code> or <code>ga</code> or <code>final</code>
 * </li>
 * <li><code>sp</code></li>
 * </ul>
 * Unknown qualifiers are considered after known qualifiers, with lexical order
 * (always case insensitive),</li>
 * <li>a dash usually precedes a qualifier, and is always less important than
 * something preceded with a dot.</li>
 * </ul>
 * </p>
 *
 * @author <a href="mailto:kenney@apache.org">Kenney Westerhof</a>
 * @author <a href="mailto:hboutemy@apache.org">Hervé Boutemy</a>
 * @see <a href=
 *      "https://cwiki.apache.org/confluence/display/MAVENOLD/Versioning">
 *      "Versioning" on Maven Wiki</a>
 */
public class ComparableVersion implements Comparable<ComparableVersion> {
    
    /** The value. */
    private String value;

    /** The canonical. */
    private String canonical;

    /** The items. */
    private ListItem items;

    /**
     * The Interface Item.
     */
    private interface Item {
        
        /** The integer item. */
        int INTEGER_ITEM = 0;
        
        /** The string item. */
        int STRING_ITEM = 1;
        
        /** The list item. */
        int LIST_ITEM = 2;

        /**
         * Compare to.
         *
         * @param item the item
         * @return the int
         */
        int compareTo(Item item);

        /**
         * Gets the type.
         *
         * @return the type
         */
        int getType();

        /**
         * Checks if is null.
         *
         * @return true, if is null
         */
        boolean isNull();
    }

    /**
     * Represents a numeric item in the version item list.
     */
    private static class IntegerItem implements Item {
        
        /** The Constant BIG_INTEGER_ZERO. */
        private static final BigInteger BIG_INTEGER_ZERO = new BigInteger("0");

        /** The value. */
        private final BigInteger value;

        /** The Constant ZERO. */
        public static final IntegerItem ZERO = new IntegerItem();

        /**
         * Instantiates a new integer item.
         */
        private IntegerItem() {
            this.value = BIG_INTEGER_ZERO;
        }

        /**
         * Instantiates a new integer item.
         *
         * @param str the str
         */
        public IntegerItem(String str) {
            this.value = new BigInteger(str);
        }

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#getType()
         */
        public int getType() {
            return INTEGER_ITEM;
        }

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#isNull()
         */
        public boolean isNull() {
            return BIG_INTEGER_ZERO.equals(value);
        }

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#compareTo(org.hotswap.agent.versions.ComparableVersion.Item)
         */
        public int compareTo(Item item) {
            if (item == null) {
                return BIG_INTEGER_ZERO.equals(value) ? 0 : 1; // 1.0 == 1, 1.1
                                                               // > 1
            }

            switch (item.getType()) {
            case INTEGER_ITEM:
                return value.compareTo(((IntegerItem) item).value);

            case STRING_ITEM:
                return 1; // 1.1 > 1-sp

            case LIST_ITEM:
                return 1; // 1.1 > 1-1

            default:
                throw new RuntimeException("invalid item: " + item.getClass());
            }
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        public String toString() {
            return value.toString();
        }
    }

    /**
     * Represents a string in the version item list, usually a qualifier.
     */
    private static class StringItem implements Item {
        
        /** The Constant QUALIFIERS. */
        private static final String[] QUALIFIERS = { "alpha", "beta", "milestone", "rc", "snapshot", "", "sp" };

        /** The Constant AR_QUALIFIERS. */
        private static final List<String> AR_QUALIFIERS = Arrays.asList(QUALIFIERS);

        /** The Constant ALIASES. */
        private static final Properties ALIASES = new Properties();

        static {
            ALIASES.put("ga", "");
            ALIASES.put("final", "");
            ALIASES.put("cr", "rc");
        }

        /**
         * A comparable value for the empty-string qualifier. This one is used
         * to determine if a given qualifier makes the version older than one
         * without a qualifier, or more recent.
         */
        private static final String RELEASE_VERSION_INDEX = String.valueOf(AR_QUALIFIERS.indexOf(""));

        /** The value. */
        private String value;

        /**
         * Instantiates a new string item.
         *
         * @param value the value
         * @param followedByDigit the followed by digit
         */
        public StringItem(String value, boolean followedByDigit) {
            if (followedByDigit && value.length() == 1) {
                // a1 = alpha-1, b1 = beta-1, m1 = milestone-1
                switch (value.charAt(0)) {
                case 'a':
                    value = "alpha";
                    break;
                case 'b':
                    value = "beta";
                    break;
                case 'm':
                    value = "milestone";
                    break;
                default:
                }
            }
            this.value = ALIASES.getProperty(value, value);
        }

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#getType()
         */
        public int getType() {
            return STRING_ITEM;
        }

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#isNull()
         */
        public boolean isNull() {
            return (comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX) == 0);
        }

        /**
         * Returns a comparable value for a qualifier.
         * 
         * This method takes into account the ordering of known qualifiers then
         * unknown qualifiers with lexical ordering.
         * 
         * just returning an Integer with the index here is faster, but requires
         * a lot of if/then/else to check for -1 or QUALIFIERS.size and then
         * resort to lexical ordering. Most comparisons are decided by the first
         * character, so this is still fast. If more characters are needed then
         * it requires a lexical sort anyway.
         *
         * @param qualifier the qualifier
         * @return an equivalent value that can be used with lexical comparison
         */
        public static String comparableQualifier(String qualifier) {
            int i = AR_QUALIFIERS.indexOf(qualifier);

            return i == -1 ? (AR_QUALIFIERS.size() + "-" + qualifier) : String.valueOf(i);
        }

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#compareTo(org.hotswap.agent.versions.ComparableVersion.Item)
         */
        public int compareTo(Item item) {
            if (item == null) {
                // 1-rc < 1, 1-ga > 1
                return comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX);
            }
            switch (item.getType()) {
            case INTEGER_ITEM:
                return -1; // 1.any < 1.1 ?

            case STRING_ITEM:
                return comparableQualifier(value).compareTo(comparableQualifier(((StringItem) item).value));

            case LIST_ITEM:
                return -1; // 1.any < 1-1

            default:
                throw new RuntimeException("invalid item: " + item.getClass());
            }
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        public String toString() {
            return value;
        }
    }

    /**
     * Represents a version list item. This class is used both for the global
     * item list and for sub-lists (which start with '-(number)' in the version
     * specification).
     */
    private static class ListItem extends ArrayList<Item>implements Item {
        
        /** The Constant serialVersionUID. */
        private static final long serialVersionUID = 1L;

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#getType()
         */
        public int getType() {
            return LIST_ITEM;
        }

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#isNull()
         */
        public boolean isNull() {
            return (size() == 0);
        }

        /**
         * Normalize.
         */
        void normalize() {
            for (int i = size() - 1; i >= 0; i--) {
                Item lastItem = get(i);

                if (lastItem.isNull()) {
                    // remove null trailing items: 0, "", empty list
                    remove(i);
                } else if (!(lastItem instanceof ListItem)) {
                    break;
                }
            }
        }

        /* (non-Javadoc)
         * @see org.hotswap.agent.versions.ComparableVersion.Item#compareTo(org.hotswap.agent.versions.ComparableVersion.Item)
         */
        public int compareTo(Item item) {
            if (item == null) {
                if (size() == 0) {
                    return 0; // 1-0 = 1- (normalize) = 1
                }
                Item first = get(0);
                return first.compareTo(null);
            }
            switch (item.getType()) {
            case INTEGER_ITEM:
                return -1; // 1-1 < 1.0.x

            case STRING_ITEM:
                return 1; // 1-1 > 1-sp

            case LIST_ITEM:
                Iterator<Item> left = iterator();
                Iterator<Item> right = ((ListItem) item).iterator();

                while (left.hasNext() || right.hasNext()) {
                    Item l = left.hasNext() ? left.next() : null;
                    Item r = right.hasNext() ? right.next() : null;

                    // if this is shorter, then invert the compare and mul with
                    // -1
                    int result = l == null ? (r == null ? 0 : -1 * r.compareTo(l)) : l.compareTo(r);

                    if (result != 0) {
                        return result;
                    }
                }

                return 0;

            default:
                throw new RuntimeException("invalid item: " + item.getClass());
            }
        }

        /* (non-Javadoc)
         * @see java.util.AbstractCollection#toString()
         */
        public String toString() {
            StringBuilder buffer = new StringBuilder();
            for (Item item : this) {
                if (buffer.length() > 0) {
                    buffer.append((item instanceof ListItem) ? '-' : '.');
                }
                buffer.append(item);
            }
            return buffer.toString();
        }
    }

    /**
     * Instantiates a new comparable version.
     *
     * @param version the version
     */
    public ComparableVersion(String version) {
        parseVersion(version);
    }

    /**
     * Parses the version.
     *
     * @param version the version
     */
    public final void parseVersion(String version) {
        this.value = version;

        items = new ListItem();

        version = version.toLowerCase(Locale.ENGLISH);

        ListItem list = items;

        Stack<Item> stack = new Stack<>();
        stack.push(list);

        boolean isDigit = false;

        int startIndex = 0;

        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);

            if (c == '.') {
                if (i == startIndex) {
                    list.add(IntegerItem.ZERO);
                } else {
                    list.add(parseItem(isDigit, version.substring(startIndex, i)));
                }
                startIndex = i + 1;
            } else if (c == '-') {
                if (i == startIndex) {
                    list.add(IntegerItem.ZERO);
                } else {
                    list.add(parseItem(isDigit, version.substring(startIndex, i)));
                }
                startIndex = i + 1;

                list.add(list = new ListItem());
                stack.push(list);
            } else if (Character.isDigit(c)) {
                if (!isDigit && i > startIndex) {
                    list.add(new StringItem(version.substring(startIndex, i), true));
                    startIndex = i;

                    list.add(list = new ListItem());
                    stack.push(list);
                }

                isDigit = true;
            } else {
                if (isDigit && i > startIndex) {
                    list.add(parseItem(true, version.substring(startIndex, i)));
                    startIndex = i;

                    list.add(list = new ListItem());
                    stack.push(list);
                }

                isDigit = false;
            }
        }

        if (version.length() > startIndex) {
            list.add(parseItem(isDigit, version.substring(startIndex)));
        }

        while (!stack.isEmpty()) {
            list = (ListItem) stack.pop();
            list.normalize();
        }

        canonical = items.toString();
    }

    /**
     * Parses the item.
     *
     * @param isDigit the is digit
     * @param buf the buf
     * @return the item
     */
    private static Item parseItem(boolean isDigit, String buf) {
        return isDigit ? new IntegerItem(buf) : new StringItem(buf, false);
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(ComparableVersion o) {
        return items.compareTo(o.items);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return value;
    }

    /**
     * Gets the canonical.
     *
     * @return the canonical
     */
    public String getCanonical() {
        return canonical;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object o) {
        return (o instanceof ComparableVersion) && canonical.equals(ComparableVersion.class.cast(o).canonical);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return canonical.hashCode();
    }

    /**
     * Main to test version parsing and comparison.
     *
     * @param args
     *            the version strings to parse and compare
     */
    public static void main(String... args) {
        System.out.println("Display parameters as parsed by Maven (in canonical form) and comparison result:");
        if (args.length == 0) {
            return;
        }

        ComparableVersion prev = null;
        int i = 1;
        for (String version : args) {
            ComparableVersion c = new ComparableVersion(version);

            if (prev != null) {
                int compare = prev.compareTo(c);
                System.out.println("   " + prev.toString() + ' ' + ((compare == 0) ? "==" : ((compare < 0) ? "<" : ">")) + ' ' + version);
            }

            System.out.println(String.valueOf(i++) + ". " + version + " == " + c.getCanonical());

            prev = c;
        }
    }
}
