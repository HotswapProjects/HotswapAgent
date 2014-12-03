package org.hotswap.agent.plugin.spring.signature;

import java.util.Comparator;

/**
 * Compares by toString result
 * @author Erki Ehtla
 * 
 */
public final class ToStringComparator implements Comparator<Object> {
	public static final ToStringComparator INSTANCE = new ToStringComparator();
	
	@Override
	public int compare(Object o1, Object o2) {
		return o1.toString().compareTo(o2.toString());
	}
}