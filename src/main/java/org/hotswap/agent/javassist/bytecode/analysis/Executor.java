/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 1999- Shigeru Chiba. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */
package org.hotswap.agent.javassist.bytecode.analysis;

import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.bytecode.BadBytecode;
import org.hotswap.agent.javassist.bytecode.ConstPool;
import org.hotswap.agent.javassist.bytecode.Descriptor;
import org.hotswap.agent.javassist.bytecode.MethodInfo;

/**
 * Executor is responsible for modeling the effects of a JVM instruction on a frame.
 *
 * @author Jason T. Greene
 */
public class Executor implements org.hotswap.agent.javassist.bytecode.Opcode {
    private final ConstPool constPool;
    private final org.hotswap.agent.javassist.ClassPool classPool;
    private final org.hotswap.agent.javassist.bytecode.analysis.Type STRING_TYPE;
    private final org.hotswap.agent.javassist.bytecode.analysis.Type CLASS_TYPE;
    private final org.hotswap.agent.javassist.bytecode.analysis.Type THROWABLE_TYPE;
    private int lastPos;

    public Executor(org.hotswap.agent.javassist.ClassPool classPool, ConstPool constPool) {
        this.constPool = constPool;
        this.classPool = classPool;

        try {
            STRING_TYPE = getType("java.lang.String");
            CLASS_TYPE = getType("java.lang.Class");
            THROWABLE_TYPE = getType("java.lang.Throwable");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Execute the instruction, modeling the effects on the specified frame and subroutine.
     * If a subroutine is passed, the access flags will be modified if this instruction accesses
     * the local variable table.
     *
     * @param method     the method containing the instruction
     * @param pos        the position of the instruction in the method
     * @param iter       the code iterator used to find the instruction
     * @param frame      the frame to modify to represent the result of the instruction
     * @param subroutine the optional subroutine this instruction belongs to.
     * @throws BadBytecode if the bytecode violates the jvm spec
     */
    public void execute(MethodInfo method, int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iter, Frame frame, Subroutine subroutine) throws BadBytecode {
        this.lastPos = pos;
        int opcode = iter.byteAt(pos);


        // Declared opcode in order
        switch (opcode) {
            case NOP:
                break;
            case ACONST_NULL:
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.UNINIT);
                break;
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER);
                break;
            case LCONST_0:
            case LCONST_1:
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG);
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.TOP);
                break;
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT);
                break;
            case DCONST_0:
            case DCONST_1:
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE);
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.TOP);
                break;
            case BIPUSH:
            case SIPUSH:
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER);
                break;
            case LDC:
                evalLDC(iter.byteAt(pos + 1), frame);
                break;
            case LDC_W:
            case LDC2_W:
                evalLDC(iter.u16bitAt(pos + 1), frame);
                break;
            case ILOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case LLOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case FLOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case DLOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case ALOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, opcode - ILOAD_0, frame, subroutine);
                break;
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, opcode - LLOAD_0, frame, subroutine);
                break;
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, opcode - FLOAD_0, frame, subroutine);
                break;
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, opcode - DLOAD_0, frame, subroutine);
                break;
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, opcode - ALOAD_0, frame, subroutine);
                break;
            case IALOAD:
                evalArrayLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LALOAD:
                evalArrayLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case FALOAD:
                evalArrayLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case DALOAD:
                evalArrayLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case AALOAD:
                evalArrayLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, frame);
                break;
            case BALOAD:
            case CALOAD:
            case SALOAD:
                evalArrayLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case ISTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case LSTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case FSTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case DSTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case ASTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, iter.byteAt(pos + 1), frame, subroutine);
                break;
            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, opcode - ISTORE_0, frame, subroutine);
                break;
            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, opcode - LSTORE_0, frame, subroutine);
                break;
            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, opcode - FSTORE_0, frame, subroutine);
                break;
            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, opcode - DSTORE_0, frame, subroutine);
                break;
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, opcode - ASTORE_0, frame, subroutine);
                break;
            case IASTORE:
                evalArrayStore(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LASTORE:
                evalArrayStore(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case FASTORE:
                evalArrayStore(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case DASTORE:
                evalArrayStore(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case AASTORE:
                evalArrayStore(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, frame);
                break;
            case BASTORE:
            case CASTORE:
            case SASTORE:
                evalArrayStore(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case POP:
                if (frame.pop() == org.hotswap.agent.javassist.bytecode.analysis.Type.TOP)
                    throw new BadBytecode("POP can not be used with a category 2 value, pos = " + pos);
                break;
            case POP2:
                frame.pop();
                frame.pop();
                break;
            case DUP: {
                org.hotswap.agent.javassist.bytecode.analysis.Type type = frame.peek();
                if (type == org.hotswap.agent.javassist.bytecode.analysis.Type.TOP)
                    throw new BadBytecode("DUP can not be used with a category 2 value, pos = " + pos);

                frame.push(frame.peek());
                break;
            }
            case DUP_X1:
            case DUP_X2: {
                org.hotswap.agent.javassist.bytecode.analysis.Type type = frame.peek();
                if (type == org.hotswap.agent.javassist.bytecode.analysis.Type.TOP)
                    throw new BadBytecode("DUP can not be used with a category 2 value, pos = " + pos);
                int end = frame.getTopIndex();
                int insert = end - (opcode - DUP_X1) - 1;
                frame.push(type);

                while (end > insert) {
                    frame.setStack(end, frame.getStack(end - 1));
                    end--;
                }
                frame.setStack(insert, type);
                break;
            }
            case DUP2:
                frame.push(frame.getStack(frame.getTopIndex() - 1));
                frame.push(frame.getStack(frame.getTopIndex() - 1));
                break;
            case DUP2_X1:
            case DUP2_X2: {
                int end = frame.getTopIndex();
                int insert = end - (opcode - DUP2_X1) - 1;
                org.hotswap.agent.javassist.bytecode.analysis.Type type1 = frame.getStack(frame.getTopIndex() - 1);
                org.hotswap.agent.javassist.bytecode.analysis.Type type2 = frame.peek();
                frame.push(type1);
                frame.push(type2);
                while (end > insert) {
                    frame.setStack(end, frame.getStack(end - 2));
                    end--;
                }
                frame.setStack(insert, type2);
                frame.setStack(insert - 1, type1);
                break;
            }
            case SWAP: {
                org.hotswap.agent.javassist.bytecode.analysis.Type type1 = frame.pop();
                org.hotswap.agent.javassist.bytecode.analysis.Type type2 = frame.pop();
                if (type1.getSize() == 2 || type2.getSize() == 2)
                    throw new BadBytecode("Swap can not be used with category 2 values, pos = " + pos);
                frame.push(type1);
                frame.push(type2);
                break;
            }

            // Math
            case IADD:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LADD:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case FADD:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case DADD:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case ISUB:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LSUB:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case FSUB:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case DSUB:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case IMUL:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LMUL:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case FMUL:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case DMUL:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case IDIV:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LDIV:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case FDIV:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case DDIV:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case IREM:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LREM:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case FREM:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case DREM:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;

            // Unary
            case INEG:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePeek(frame));
                break;
            case LNEG:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, simplePeek(frame));
                break;
            case FNEG:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, simplePeek(frame));
                break;
            case DNEG:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, simplePeek(frame));
                break;

            // Shifts
            case ISHL:
                evalShift(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LSHL:
                evalShift(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case ISHR:
                evalShift(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LSHR:
                evalShift(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case IUSHR:
                evalShift(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LUSHR:
                evalShift(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;

            // Bitwise Math
            case IAND:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LAND:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case IOR:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LOR:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case IXOR:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case LXOR:
                evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;

            case IINC: {
                int index = iter.byteAt(pos + 1);
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame.getLocal(index));
                access(index, org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, subroutine);
                break;
            }

            // Conversion
            case I2L:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case I2F:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case I2D:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case L2I:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case L2F:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case L2D:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case F2I:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case F2L:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case F2D:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, frame);
                break;
            case D2I:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame);
                break;
            case D2L:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, frame);
                break;
            case D2F:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, simplePop(frame));
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, frame);
                break;
            case I2B:
            case I2C:
            case I2S:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame.peek());
                break;
            case LCMP:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, simplePop(frame));
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, simplePop(frame));
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER);
                break;
            case FCMPL:
            case FCMPG:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, simplePop(frame));
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, simplePop(frame));
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER);
                break;
            case DCMPL:
            case DCMPG:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, simplePop(frame));
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, simplePop(frame));
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER);
                break;

            // Control flow
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
                break;
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
                break;
            case IF_ACMPEQ:
            case IF_ACMPNE:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, simplePop(frame));
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, simplePop(frame));
                break;
            case GOTO:
                break;
            case JSR:
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.RETURN_ADDRESS);
                break;
            case RET:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.RETURN_ADDRESS, frame.getLocal(iter.byteAt(pos + 1)));
                break;
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
                break;
            case LRETURN:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, simplePop(frame));
                break;
            case FRETURN:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, simplePop(frame));
                break;
            case DRETURN:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, simplePop(frame));
                break;
            case ARETURN:
                try {
                    CtClass returnType = Descriptor.getReturnType(method.getDescriptor(), classPool);
                    verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.get(returnType), simplePop(frame));
                } catch (NotFoundException e) {
                    throw new RuntimeException(e);
                }
                break;
            case RETURN:
                break;
            case GETSTATIC:
                evalGetField(opcode, iter.u16bitAt(pos + 1), frame);
                break;
            case PUTSTATIC:
                evalPutField(opcode, iter.u16bitAt(pos + 1), frame);
                break;
            case GETFIELD:
                evalGetField(opcode, iter.u16bitAt(pos + 1), frame);
                break;
            case PUTFIELD:
                evalPutField(opcode, iter.u16bitAt(pos + 1), frame);
                break;
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
                evalInvokeMethod(opcode, iter.u16bitAt(pos + 1), frame);
                break;
            case INVOKEINTERFACE:
                evalInvokeIntfMethod(opcode, iter.u16bitAt(pos + 1), frame);
                break;
            case INVOKEDYNAMIC:
                evalInvokeDynamic(opcode, iter.u16bitAt(pos + 1), frame);
                break;
            case NEW:
                frame.push(resolveClassInfo(constPool.getClassInfo(iter.u16bitAt(pos + 1))));
                break;
            case NEWARRAY:
                evalNewArray(pos, iter, frame);
                break;
            case ANEWARRAY:
                evalNewObjectArray(pos, iter, frame);
                break;
            case ARRAYLENGTH: {
                org.hotswap.agent.javassist.bytecode.analysis.Type array = simplePop(frame);
                if (!array.isArray() && array != org.hotswap.agent.javassist.bytecode.analysis.Type.UNINIT)
                    throw new BadBytecode("Array length passed a non-array [pos = " + pos + "]: " + array);
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER);
                break;
            }
            case ATHROW:
                verifyAssignable(THROWABLE_TYPE, simplePop(frame));
                break;
            case CHECKCAST:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, simplePop(frame));
                frame.push(typeFromDesc(constPool.getClassInfoByDescriptor(iter.u16bitAt(pos + 1))));
                break;
            case INSTANCEOF:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, simplePop(frame));
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER);
                break;
            case MONITORENTER:
            case MONITOREXIT:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, simplePop(frame));
                break;
            case WIDE:
                evalWide(pos, iter, frame, subroutine);
                break;
            case MULTIANEWARRAY:
                evalNewObjectArray(pos, iter, frame);
                break;
            case IFNULL:
            case IFNONNULL:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, simplePop(frame));
                break;
            case GOTO_W:
                break;
            case JSR_W:
                frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.RETURN_ADDRESS);
                break;
        }
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type zeroExtend(org.hotswap.agent.javassist.bytecode.analysis.Type type) {
        if (type == org.hotswap.agent.javassist.bytecode.analysis.Type.SHORT || type == org.hotswap.agent.javassist.bytecode.analysis.Type.BYTE || type == org.hotswap.agent.javassist.bytecode.analysis.Type.CHAR || type == org.hotswap.agent.javassist.bytecode.analysis.Type.BOOLEAN)
            return org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER;

        return type;
    }

    private void evalArrayLoad(org.hotswap.agent.javassist.bytecode.analysis.Type expectedComponent, Frame frame) throws BadBytecode {
        org.hotswap.agent.javassist.bytecode.analysis.Type index = frame.pop();
        org.hotswap.agent.javassist.bytecode.analysis.Type array = frame.pop();

        // Special case, an array defined by aconst_null
        // TODO - we might need to be more inteligent about this
        if (array == org.hotswap.agent.javassist.bytecode.analysis.Type.UNINIT) {
            verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, index);
            if (expectedComponent == org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT) {
                simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type.UNINIT, frame);
            } else {
                simplePush(expectedComponent, frame);
            }
            return;
        }

        org.hotswap.agent.javassist.bytecode.analysis.Type component = array.getComponent();

        if (component == null)
            throw new BadBytecode("Not an array! [pos = " + lastPos + "]: " + component);

        component = zeroExtend(component);

        verifyAssignable(expectedComponent, component);
        verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, index);
        simplePush(component, frame);
    }

    private void evalArrayStore(org.hotswap.agent.javassist.bytecode.analysis.Type expectedComponent, Frame frame) throws BadBytecode {
        org.hotswap.agent.javassist.bytecode.analysis.Type value = simplePop(frame);
        org.hotswap.agent.javassist.bytecode.analysis.Type index = frame.pop();
        org.hotswap.agent.javassist.bytecode.analysis.Type array = frame.pop();

        if (array == org.hotswap.agent.javassist.bytecode.analysis.Type.UNINIT) {
            verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, index);
            return;
        }

        org.hotswap.agent.javassist.bytecode.analysis.Type component = array.getComponent();

        if (component == null)
            throw new BadBytecode("Not an array! [pos = " + lastPos + "]: " + component);

        component = zeroExtend(component);

        verifyAssignable(expectedComponent, component);
        verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, index);

        // This intentionally only checks for Object on aastore
        // downconverting of an array (no casts)
        // e.g. Object[] blah = new String[];
        //      blah[2] = (Object) "test";
        //      blah[3] = new Integer(); // compiler doesnt catch it (has legal bytecode),
        //                               // but will throw arraystoreexception
        if (expectedComponent == org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT) {
            verifyAssignable(expectedComponent, value);
        } else {
            verifyAssignable(component, value);
        }
    }

    private void evalBinaryMath(org.hotswap.agent.javassist.bytecode.analysis.Type expected, Frame frame) throws BadBytecode {
        org.hotswap.agent.javassist.bytecode.analysis.Type value2 = simplePop(frame);
        org.hotswap.agent.javassist.bytecode.analysis.Type value1 = simplePop(frame);

        verifyAssignable(expected, value2);
        verifyAssignable(expected, value1);
        simplePush(value1, frame);
    }

    private void evalGetField(int opcode, int index, Frame frame) throws BadBytecode {
        String desc = constPool.getFieldrefType(index);
        org.hotswap.agent.javassist.bytecode.analysis.Type type = zeroExtend(typeFromDesc(desc));

        if (opcode == GETFIELD) {
            org.hotswap.agent.javassist.bytecode.analysis.Type objectType = resolveClassInfo(constPool.getFieldrefClassName(index));
            verifyAssignable(objectType, simplePop(frame));
        }

        simplePush(type, frame);
    }

    private void evalInvokeIntfMethod(int opcode, int index, Frame frame) throws BadBytecode {
        String desc = constPool.getInterfaceMethodrefType(index);
        org.hotswap.agent.javassist.bytecode.analysis.Type[] types = paramTypesFromDesc(desc);
        int i = types.length;

        while (i > 0)
            verifyAssignable(zeroExtend(types[--i]), simplePop(frame));

        String classInfo = constPool.getInterfaceMethodrefClassName(index);
        org.hotswap.agent.javassist.bytecode.analysis.Type objectType = resolveClassInfo(classInfo);
        verifyAssignable(objectType, simplePop(frame));

        org.hotswap.agent.javassist.bytecode.analysis.Type returnType = returnTypeFromDesc(desc);
        if (returnType != org.hotswap.agent.javassist.bytecode.analysis.Type.VOID)
            simplePush(zeroExtend(returnType), frame);
    }

    private void evalInvokeMethod(int opcode, int index, Frame frame) throws BadBytecode {
        String desc = constPool.getMethodrefType(index);
        org.hotswap.agent.javassist.bytecode.analysis.Type[] types = paramTypesFromDesc(desc);
        int i = types.length;

        while (i > 0)
            verifyAssignable(zeroExtend(types[--i]), simplePop(frame));

        if (opcode != INVOKESTATIC) {
            org.hotswap.agent.javassist.bytecode.analysis.Type objectType = resolveClassInfo(constPool.getMethodrefClassName(index));
            verifyAssignable(objectType, simplePop(frame));
        }

        org.hotswap.agent.javassist.bytecode.analysis.Type returnType = returnTypeFromDesc(desc);
        if (returnType != org.hotswap.agent.javassist.bytecode.analysis.Type.VOID)
            simplePush(zeroExtend(returnType), frame);
    }

    private void evalInvokeDynamic(int opcode, int index, Frame frame) throws BadBytecode {
        String desc = constPool.getInvokeDynamicType(index);
        org.hotswap.agent.javassist.bytecode.analysis.Type[] types = paramTypesFromDesc(desc);
        int i = types.length;

        while (i > 0)
            verifyAssignable(zeroExtend(types[--i]), simplePop(frame));

        // simplePop(frame);    // assume CosntPool#REF_invokeStatic

        org.hotswap.agent.javassist.bytecode.analysis.Type returnType = returnTypeFromDesc(desc);
        if (returnType != org.hotswap.agent.javassist.bytecode.analysis.Type.VOID)
            simplePush(zeroExtend(returnType), frame);
    }

    private void evalLDC(int index, Frame frame) throws BadBytecode {
        int tag = constPool.getTag(index);
        org.hotswap.agent.javassist.bytecode.analysis.Type type;
        switch (tag) {
            case ConstPool.CONST_String:
                type = STRING_TYPE;
                break;
            case ConstPool.CONST_Integer:
                type = org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER;
                break;
            case ConstPool.CONST_Float:
                type = org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT;
                break;
            case ConstPool.CONST_Long:
                type = org.hotswap.agent.javassist.bytecode.analysis.Type.LONG;
                break;
            case ConstPool.CONST_Double:
                type = org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE;
                break;
            case ConstPool.CONST_Class:
                type = CLASS_TYPE;
                break;
            default:
                throw new BadBytecode("bad LDC [pos = " + lastPos + "]: " + tag);
        }

        simplePush(type, frame);
    }

    private void evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type expected, int index, Frame frame, Subroutine subroutine) throws BadBytecode {
        org.hotswap.agent.javassist.bytecode.analysis.Type type = frame.getLocal(index);

        verifyAssignable(expected, type);

        simplePush(type, frame);
        access(index, type, subroutine);
    }

    private void evalNewArray(int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iter, Frame frame) throws BadBytecode {
        verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
        org.hotswap.agent.javassist.bytecode.analysis.Type type = null;
        int typeInfo = iter.byteAt(pos + 1);
        switch (typeInfo) {
            case T_BOOLEAN:
                type = getType("boolean[]");
                break;
            case T_CHAR:
                type = getType("char[]");
                break;
            case T_BYTE:
                type = getType("byte[]");
                break;
            case T_SHORT:
                type = getType("short[]");
                break;
            case T_INT:
                type = getType("int[]");
                break;
            case T_LONG:
                type = getType("long[]");
                break;
            case T_FLOAT:
                type = getType("float[]");
                break;
            case T_DOUBLE:
                type = getType("double[]");
                break;
            default:
                throw new BadBytecode("Invalid array type [pos = " + pos + "]: " + typeInfo);

        }

        frame.push(type);
    }

    private void evalNewObjectArray(int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iter, Frame frame) throws BadBytecode {
        // Convert to x[] format
        org.hotswap.agent.javassist.bytecode.analysis.Type type = resolveClassInfo(constPool.getClassInfo(iter.u16bitAt(pos + 1)));
        String name = type.getCtClass().getName();
        int opcode = iter.byteAt(pos);
        int dimensions;

        if (opcode == MULTIANEWARRAY) {
            dimensions = iter.byteAt(pos + 3);
        } else {
            name = name + "[]";
            dimensions = 1;
        }

        while (dimensions-- > 0) {
            verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, simplePop(frame));
        }

        simplePush(getType(name), frame);
    }

    private void evalPutField(int opcode, int index, Frame frame) throws BadBytecode {
        String desc = constPool.getFieldrefType(index);
        org.hotswap.agent.javassist.bytecode.analysis.Type type = zeroExtend(typeFromDesc(desc));

        verifyAssignable(type, simplePop(frame));

        if (opcode == PUTFIELD) {
            org.hotswap.agent.javassist.bytecode.analysis.Type objectType = resolveClassInfo(constPool.getFieldrefClassName(index));
            verifyAssignable(objectType, simplePop(frame));
        }
    }

    private void evalShift(org.hotswap.agent.javassist.bytecode.analysis.Type expected, Frame frame) throws BadBytecode {
        org.hotswap.agent.javassist.bytecode.analysis.Type value2 = simplePop(frame);
        org.hotswap.agent.javassist.bytecode.analysis.Type value1 = simplePop(frame);

        verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, value2);
        verifyAssignable(expected, value1);
        simplePush(value1, frame);
    }

    private void evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type expected, int index, Frame frame, Subroutine subroutine) throws BadBytecode {
        org.hotswap.agent.javassist.bytecode.analysis.Type type = simplePop(frame);

        // RETURN_ADDRESS is allowed by ASTORE
        if (!(expected == org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT && type == org.hotswap.agent.javassist.bytecode.analysis.Type.RETURN_ADDRESS))
            verifyAssignable(expected, type);
        simpleSetLocal(index, type, frame);
        access(index, type, subroutine);
    }

    private void evalWide(int pos, org.hotswap.agent.javassist.bytecode.CodeIterator iter, Frame frame, Subroutine subroutine) throws BadBytecode {
        int opcode = iter.byteAt(pos + 1);
        int index = iter.u16bitAt(pos + 2);
        switch (opcode) {
            case ILOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, index, frame, subroutine);
                break;
            case LLOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, index, frame, subroutine);
                break;
            case FLOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, index, frame, subroutine);
                break;
            case DLOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, index, frame, subroutine);
                break;
            case ALOAD:
                evalLoad(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, index, frame, subroutine);
                break;
            case ISTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, index, frame, subroutine);
                break;
            case LSTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.LONG, index, frame, subroutine);
                break;
            case FSTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.FLOAT, index, frame, subroutine);
                break;
            case DSTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.DOUBLE, index, frame, subroutine);
                break;
            case ASTORE:
                evalStore(org.hotswap.agent.javassist.bytecode.analysis.Type.OBJECT, index, frame, subroutine);
                break;
            case IINC:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.INTEGER, frame.getLocal(index));
                break;
            case RET:
                verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type.RETURN_ADDRESS, frame.getLocal(index));
                break;
            default:
                throw new BadBytecode("Invalid WIDE operand [pos = " + pos + "]: " + opcode);
        }

    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type getType(String name) throws BadBytecode {
        try {
            return org.hotswap.agent.javassist.bytecode.analysis.Type.get(classPool.get(name));
        } catch (NotFoundException e) {
            throw new BadBytecode("Could not find class [pos = " + lastPos + "]: " + name);
        }
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type[] paramTypesFromDesc(String desc) throws BadBytecode {
        CtClass classes[] = null;
        try {
            classes = Descriptor.getParameterTypes(desc, classPool);
        } catch (NotFoundException e) {
            throw new BadBytecode("Could not find class in descriptor [pos = " + lastPos + "]: " + e.getMessage());
        }

        if (classes == null)
            throw new BadBytecode("Could not obtain parameters for descriptor [pos = " + lastPos + "]: " + desc);

        org.hotswap.agent.javassist.bytecode.analysis.Type[] types = new org.hotswap.agent.javassist.bytecode.analysis.Type[classes.length];
        for (int i = 0; i < types.length; i++)
            types[i] = org.hotswap.agent.javassist.bytecode.analysis.Type.get(classes[i]);

        return types;
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type returnTypeFromDesc(String desc) throws BadBytecode {
        CtClass clazz = null;
        try {
            clazz = Descriptor.getReturnType(desc, classPool);
        } catch (NotFoundException e) {
            throw new BadBytecode("Could not find class in descriptor [pos = " + lastPos + "]: " + e.getMessage());
        }

        if (clazz == null)
            throw new BadBytecode("Could not obtain return type for descriptor [pos = " + lastPos + "]: " + desc);

        return org.hotswap.agent.javassist.bytecode.analysis.Type.get(clazz);
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type simplePeek(Frame frame) {
        org.hotswap.agent.javassist.bytecode.analysis.Type type = frame.peek();
        return (type == org.hotswap.agent.javassist.bytecode.analysis.Type.TOP) ? frame.getStack(frame.getTopIndex() - 1) : type;
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type simplePop(Frame frame) {
        org.hotswap.agent.javassist.bytecode.analysis.Type type = frame.pop();
        return (type == org.hotswap.agent.javassist.bytecode.analysis.Type.TOP) ? frame.pop() : type;
    }

    private void simplePush(org.hotswap.agent.javassist.bytecode.analysis.Type type, Frame frame) {
        frame.push(type);
        if (type.getSize() == 2)
            frame.push(org.hotswap.agent.javassist.bytecode.analysis.Type.TOP);
    }

    private void access(int index, org.hotswap.agent.javassist.bytecode.analysis.Type type, Subroutine subroutine) {
        if (subroutine == null)
            return;
        subroutine.access(index);
        if (type.getSize() == 2)
            subroutine.access(index + 1);
    }

    private void simpleSetLocal(int index, org.hotswap.agent.javassist.bytecode.analysis.Type type, Frame frame) {
        frame.setLocal(index, type);
        if (type.getSize() == 2)
            frame.setLocal(index + 1, org.hotswap.agent.javassist.bytecode.analysis.Type.TOP);
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type resolveClassInfo(String info) throws BadBytecode {
        CtClass clazz = null;
        try {
            if (info.charAt(0) == '[') {
                clazz = Descriptor.toCtClass(info, classPool);
            } else {
                clazz = classPool.get(info);
            }

        } catch (NotFoundException e) {
            throw new BadBytecode("Could not find class in descriptor [pos = " + lastPos + "]: " + e.getMessage());
        }

        if (clazz == null)
            throw new BadBytecode("Could not obtain type for descriptor [pos = " + lastPos + "]: " + info);

        return org.hotswap.agent.javassist.bytecode.analysis.Type.get(clazz);
    }

    private org.hotswap.agent.javassist.bytecode.analysis.Type typeFromDesc(String desc) throws BadBytecode {
        CtClass clazz = null;
        try {
            clazz = Descriptor.toCtClass(desc, classPool);
        } catch (NotFoundException e) {
            throw new BadBytecode("Could not find class in descriptor [pos = " + lastPos + "]: " + e.getMessage());
        }

        if (clazz == null)
            throw new BadBytecode("Could not obtain type for descriptor [pos = " + lastPos + "]: " + desc);

        return org.hotswap.agent.javassist.bytecode.analysis.Type.get(clazz);
    }

    private void verifyAssignable(org.hotswap.agent.javassist.bytecode.analysis.Type expected, org.hotswap.agent.javassist.bytecode.analysis.Type type) throws BadBytecode {
        if (!expected.isAssignableFrom(type))
            throw new BadBytecode("Expected type: " + expected + " Got: " + type + " [pos = " + lastPos + "]");
    }
}
