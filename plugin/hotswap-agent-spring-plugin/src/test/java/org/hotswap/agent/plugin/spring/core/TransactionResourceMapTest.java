/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.core;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The removal of the entry of a key whose hash code has changed. The iterator of HashMap cannot do it on Java 8, so
 * the way without the iterator is tested here on every Java version.
 */
public class TransactionResourceMapTest {

    private static class Key {
        int hashCode;
        boolean hashCodeFails;
        Object equalTo;

        Key(int hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public int hashCode() {
            if (hashCodeFails) {
                throw new IllegalStateException("the bean of the key is gone");
            }
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            return other == this || (equalTo != null && other == equalTo);
        }
    }

    private final Map<Object, Object> resources = new HashMap<>();
    private final Key changed = new Key(1);
    private final Key other = new Key(2);

    private void bind() {
        resources.put(changed, "connection");
        resources.put(other, "other");
        // what a reload does
        changed.hashCode = 3;
    }

    @Test
    public void entryIsRemovedWithoutTheIterator() {
        bind();

        assertTrue(TransactionResourceMap.removeEntry(resources, changed, false));

        assertEquals(1, resources.size());
        assertEquals("other", resources.get(other));
    }

    @Test
    public void entryOfAnEqualKeyIsNotRemoved() {
        // the new proxy of the bean is bound too, and the old one is equal to it now
        Key equal = new Key(3);
        resources.put(equal, "newConnection");
        bind();
        changed.equalTo = equal;

        assertTrue(TransactionResourceMap.removeEntry(resources, changed, false));

        assertEquals(2, resources.size());
        assertEquals("newConnection", resources.get(equal));
        assertEquals("other", resources.get(other));
    }

    @Test
    public void nothingIsRemovedIfTheHashCodeOfAnotherKeyFails() {
        bind();
        other.hashCodeFails = true;

        assertFalse(TransactionResourceMap.removeEntry(resources, changed, false));

        other.hashCodeFails = false;
        assertEquals(2, resources.size());
        assertEquals("other", resources.get(other));
        assertTrue(resources.containsValue("connection"));
    }

    @Test
    public void keyThatIsNotBoundIsNotRemoved() {
        bind();

        assertFalse(TransactionResourceMap.removeEntry(resources, new Key(4), false));
        assertFalse(TransactionResourceMap.removeEntry(resources, new Key(4), true));

        assertEquals(2, resources.size());
    }
}
