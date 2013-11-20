package org.hotswap.agent.javassist.bytecode;

/**
 * Created by bubnik on 11.10.13.
 */
class ExceptionTableEntry {
    int startPc;
    int endPc;
    int handlerPc;
    int catchType;

    ExceptionTableEntry(int start, int end, int handle, int type) {
        startPc = start;
        endPc = end;
        handlerPc = handle;
        catchType = type;
    }
}
