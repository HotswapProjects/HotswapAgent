/**
 * 
 */
package org.hotswap.agent.plugin.proxy.signature.annot;

import java.util.Comparator;

import org.hotswap.agent.javassist.CtClass;

public final class CtClassComparator implements Comparator<CtClass> {
	public static final CtClassComparator INSTANCE = new CtClassComparator();
	
	@Override
	public int compare(CtClass o1, CtClass o2) {
		return o1.getName().compareTo(o2.getName());
	}
}