/*
 * Copyright 2016 the original author or authors.
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
package org.hotswap.agent.watch.nio;


import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;

/**
 * <p>
 * NIO2 watcher implementation for systems which support
 * ExtendedWatchEventModifier.FILE_TREE (windows)
 * </p>
 * <p>
 * Java 7 (NIO2) watch a directory (or tree) for changes to files.
 * </p>
 *
 * @author alpapad@gmail.com
 */
public class TreeWatcherNIO extends AbstractNIO2Watcher {

    
    private final static WatchEvent.Modifier HIGH;
    private final static WatchEvent.Modifier FILE_TREE;
    private final static WatchEvent.Modifier[] MODIFIERS;
    
    static {
        // try to set high sensitivity
        HIGH =  getWatchEventModifier("com.sun.nio.file.SensitivityWatchEventModifier","HIGH"); 
        // try to set file tree modifier
        FILE_TREE = getWatchEventModifier("com.sun.nio.file.ExtendedWatchEventModifier", "FILE_TREE");
        
        if(FILE_TREE != null) {
            MODIFIERS =  new WatchEvent.Modifier[] { FILE_TREE, HIGH };
        } else {
            MODIFIERS =  new WatchEvent.Modifier[] { HIGH };
        }
    }
    /**
	 * Instantiates a new tree watcher nio.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public TreeWatcherNIO() throws IOException {
		super();
	}

	/**
	 * Register the given directory with the WatchService.
	 *
	 * @param watched the watched path
	 * @param target the target path (could be different than watched)
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void register(Path watched, Path target) throws IOException {
		
		for(PathPair p: keys.values()) {
			// This may NOT be correct for all cases (ensure resolve will work!)
			if(p.isWatching(target)) {
				LOGGER.debug("Path {} watched via {}", target, p.getWatched());
				return;
			}
		}
	
		if (FILE_TREE == null) {
			LOGGER.debug("WATCHING:ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY - high} {}", watched);
		} else {
			LOGGER.debug("WATCHING: ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY - fileTree,high {}", watched);
		}
		
		final WatchKey key = watched.register(watcher, KINDS,  MODIFIERS);
		
		keys.put(key, new PathPair(target, watched));
	}

	/**
	 * Register the given directory,  with the
	 * WatchService. Sub-directories are automatically watched (filesystem supported)
	 *
	 * @param watched the watched
	 * @param target the target
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected void registerAll(Path watched, Path target) throws IOException {
		if(watched == null){
			watched = target.getParent();
		}
		LOGGER.info("Registering directory target {} via watched: {}", target, watched);
		
		register(watched, target);
	}
}