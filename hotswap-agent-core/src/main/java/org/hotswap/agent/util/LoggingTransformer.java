package org.hotswap.agent.util;

import org.hotswap.agent.logging.AgentLogger;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class LoggingTransformer implements ClassFileTransformer {

    private static AgentLogger LOGGER = AgentLogger.getLogger(LoggingTransformer.class);

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        // 打印类名
        LOGGER.info("Transforming class: " + className.replace('/', '.'));
        return classfileBuffer;
    }
}