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
package org.hotswap.agent.util.scanner;

import org.hotswap.agent.javassist.bytecode.AnnotationsAttribute;
import org.hotswap.agent.javassist.bytecode.ClassFile;
import org.hotswap.agent.javassist.bytecode.annotation.Annotation;
import org.hotswap.agent.javassist.bytecode.annotation.MemberValue;
import org.hotswap.agent.javassist.bytecode.annotation.StringMemberValue;
import org.hotswap.agent.logging.AgentLogger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Scan a directory for annotation returning class names.
 * <p/>
 *
 * @author Jiri Bubnik
 */
public class ClassPathAnnotationScanner {
    private static AgentLogger LOGGER = AgentLogger.getLogger(ClassPathAnnotationScanner.class);

    // Annotation name to search for
    String annotation;

    // scanner to search path
    Scanner scanner;

    /**
     * Create scanner for the annotation.
     */
    public ClassPathAnnotationScanner(String annotation, Scanner scanner) {
        this.annotation = annotation;
        this.scanner = scanner;
    }

    /**
     * Run the scan - search path for files containing annotation.
     *
     * @param classLoader classloader to resolve path
     * @param path        path to scan {@link org.hotswap.agent.util.scanner.Scanner#scan(ClassLoader, String, ScannerVisitor)}
     * @return list of class names containing the annotation
     * @throws IOException scan exception.
     */
    public List<String> scanPlugins(ClassLoader classLoader, String path) throws IOException {
        return scanPlugins(classLoader, path, name -> true);
    }

    /**
     * Run the scan - search path for files containing annotation with an accepted name.
     *
     * @param classLoader classloader to resolve path
     * @param path        path to scan {@link org.hotswap.agent.util.scanner.Scanner#scan(ClassLoader, String, ScannerVisitor)}
     * @param nameFilter  accepts the value of the annotation's name element
     * @return list of class names containing the annotation
     * @throws IOException scan exception.
     */
    public List<String> scanPlugins(ClassLoader classLoader, String path, Predicate<String> nameFilter) throws IOException {
        final List<String> files = new LinkedList<>();
        scanner.scan(classLoader, path, new ScannerVisitor() {
            @Override
            public void visit(InputStream file) throws IOException {
                ClassFile cf;
                try {
                    DataInputStream dstream = new DataInputStream(file);
                    cf = new ClassFile(dstream);
                } catch (IOException e) {
                    throw new IOException("Stream not a valid classFile", e);
                }

                Annotation ann = findAnnotation(cf);
                if (ann != null && nameFilter.test(getName(ann))) {
                    files.add(cf.getName());
                }
            }
        });
        return files;
    }

    private Annotation findAnnotation(ClassFile cf) {
        AnnotationsAttribute visible = (AnnotationsAttribute) cf.getAttribute(AnnotationsAttribute.visibleTag);
        return visible == null ? null : visible.getAnnotation(annotation);
    }

    private static String getName(Annotation ann) {
        MemberValue name = ann.getMemberValue("name");
        return name instanceof StringMemberValue ? ((StringMemberValue) name).getValue() : "";
    }


}
