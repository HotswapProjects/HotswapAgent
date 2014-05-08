package org.hotswap.agent.util.scanner;

import org.hotswap.agent.javassist.bytecode.AnnotationsAttribute;
import org.hotswap.agent.javassist.bytecode.ClassFile;
import org.hotswap.agent.javassist.bytecode.annotation.Annotation;
import org.hotswap.agent.logging.AgentLogger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

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
        final List<String> files = new LinkedList<String>();
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

                if (hasAnnotation(cf))
                    files.add(cf.getName());
            }
        });
        return files;
    }

    /**
     * Check if the file contains annotation.
     */
    protected boolean hasAnnotation(ClassFile cf) throws IOException {

        AnnotationsAttribute visible = (AnnotationsAttribute) cf.getAttribute(AnnotationsAttribute.visibleTag);
        if (visible != null) {
            for (Annotation ann : visible.getAnnotations()) {
                if (annotation.equals(ann.getTypeName())) {
                    return true;
                }
            }
        }
        return false;
    }


}
