package org.hotswap.agent.watch;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Created by bubnik on 2.11.13.
 */
public class WatcherFactoryTest {
    @Test
    public void testGetWatcher() throws Exception {
        assertNotNull(new WatcherFactory().getWatcher());
    }
}
