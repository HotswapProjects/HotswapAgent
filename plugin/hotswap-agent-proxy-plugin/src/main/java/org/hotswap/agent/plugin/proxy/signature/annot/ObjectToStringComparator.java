/**
 * 
 */
package org.hotswap.agent.plugin.proxy.signature.annot;

import java.util.Comparator;

public final class ObjectToStringComparator implements Comparator<Object> {
	public static final ObjectToStringComparator INSTANCE = new ObjectToStringComparator();
	
	@Override
	public int compare(Object o1, Object o2) {
		return o1.toString().compareTo(o2.toString());
	}
}