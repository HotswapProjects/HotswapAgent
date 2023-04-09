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
package org.hotswap.agent.plugin.proxy.api;

import java.util.Map;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.proxy.RedefinitionScheduler;

/**
 * Multistep proxy redefinition strategy. Uses Instrumentation to schedule and run next steps.
 *
 * @author Erki Ehtla
 *
 */
public abstract class MultistepProxyTransformer extends AbstractProxyTransformer
{
    private static final AgentLogger LOGGER = AgentLogger.getLogger(MultistepProxyTransformer.class);
    public static boolean addThirdStep = false;

    protected byte[] classfileBuffer;
    protected Map<Class<?>, TransformationState> transformationStates;
    protected ProxyBytecodeGenerator generator;
    protected ProxyBytecodeTransformer transformer;

    public MultistepProxyTransformer(Class<?> classBeingRedefined, ClassPool classPool, byte[] classfileBuffer,
            Map<Class<?>, TransformationState> transformationStates) {
        super(classBeingRedefined, classPool);
        this.classPool = classPool;
        this.transformationStates = transformationStates;
        this.classfileBuffer = classfileBuffer;
    }

    /**
     * Handles the current transformation state
     *
     * @return
     * @throws Exception
     */
    public byte[] transformRedefine() throws Exception {
        switch (getTransformationstate()) {
            case NEW:
                if (!isTransformingNeeded()) {
                    return classfileBuffer;
                }
                setClassAsWaiting();
                // We can't do the transformation in this event, because we can't see the changes in the class
                // definitons. Schedule a new redefinition event.
                scheduleRedefinition();
                return classfileBuffer;
            case WAITING:
                classfileBuffer = getTransformer().transform(getGenerator().generate());
                LOGGER.reload("Class '{}' has been reloaded.", classBeingRedefined.getName());
                if (addThirdStep) {
                    setClassAsFinished();
                    scheduleRedefinition();
                } else
                    removeClassState();
                return classfileBuffer;
            case FINISHED:
                removeClassState();
                return classfileBuffer;
            default:
                throw new RuntimeException("Unhandeled TransformationState!");
        }
    }

    /**
     * @return Current transformation state of the classBeingRedefined
     */
    protected TransformationState getTransformationstate() {
        TransformationState transformationState = transformationStates.get(classBeingRedefined);
        if (transformationState == null)
            transformationState = TransformationState.NEW;
        return transformationState;
    }

    /**
     * Generate new redefinition event for current classBeingRedefined
     */
    protected void scheduleRedefinition() {
        RedefinitionScheduler.schedule(this);
    }

    /**
     * Set classBeingRedefined as waiting
     *
     * @return
     */
    protected TransformationState setClassAsWaiting() {
        return transformationStates.put(classBeingRedefined, TransformationState.WAITING);
    }

    /**
     * Set classBeingRedefined as finished
     *
     * @return
     */
    protected TransformationState setClassAsFinished() {
        return transformationStates.put(classBeingRedefined, TransformationState.FINISHED);
    }

    /**
     * Remove any state associated with classBeingRedefined
     *
     * @return
     */
    public TransformationState removeClassState() {
        return transformationStates.remove(classBeingRedefined);
    }

    /**
     * The Class this instance is redefining
     *
     * @return
     */
    public Class<?> getClassBeingRedefined() {
        return classBeingRedefined;
    }

    /**
     * Bytecode of the Class this instance is redefining.
     *
     * @return
     */
    public byte[] getClassfileBuffer() {
        return classfileBuffer;
    }
}
