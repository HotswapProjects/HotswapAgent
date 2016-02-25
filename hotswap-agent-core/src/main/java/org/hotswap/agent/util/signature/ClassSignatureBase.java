package org.hotswap.agent.util.signature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ClassSignatureBase. Base class for class signature evaluation
 *
 * @author Erki Ehtla, Vladimir Dvorak
 */
public abstract class ClassSignatureBase {

    private final Set<ClassSignatureElement> elements = new HashSet<>();

    /**
     * Evaluate and return signature value
     *
     * @return the signature value
     * @throws Exception
     */
    public abstract String getValue() throws Exception;

    /**
     * Adds the signature elements to set of used signature elements
     *
     * @param elems
     */
    public void addSignatureElements(ClassSignatureElement elems[]) {
        for (ClassSignatureElement element : elems) {
            elements.add(element);
        }
    }

    /**
     * Check if given signature element is set.
     *
     * @param element
     * @return true, if has given element
     */
    public boolean hasElement(ClassSignatureElement element) {
        return elements.contains(element);
    }

    protected String annotationToString(Object[] a) {
        if (a == null)
            return "null";
        int iMax = a.length - 1;
        if (iMax == -1)
            return "[]";
        a = sort(a);
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            Annotation object = (Annotation) a[i];
            String[] ignore = new String[] { "annotationType", "equals", "hashCode", "toString" };
            Method[] declaredMethods = object.getClass().getDeclaredMethods();
            List<String> values = new ArrayList<>();
            for (Method method : declaredMethods) {
                if (Arrays.binarySearch(ignore, method.getName()) < 0) {
                    Object value = getAnnotationValue(object, method.getName());
                    if (value != null)
                        values.add(method.getName() + "=" + value.getClass() + ":" + value);
                }
            }
            b.append(values);
            // b.append(object.toString() + "()");
            if (i == iMax)
                return b.append(']').toString();
            b.append(", ");
        }
    }

    protected String annotationToString(Object[][] a) {
        if (a == null)
            return "null";

        int iMax = a.length - 1;
        if (iMax == -1)
            return "[]";
        a = sort(a);

        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            Object[] object = a[i];
            b.append(annotationToString(object));
            if (i == iMax)
                return b.append(']').toString();
            b.append(", ");
        }
    }

    private <T> T[] sort(T[] a) {
        a = Arrays.copyOf(a, a.length);
        Arrays.sort(a, ToStringComparator.INSTANCE);
        return a;
    }

    private <T> T[][] sort(T[][] a) {
        a = Arrays.copyOf(a, a.length);
        Arrays.sort(a, ToStringComparator.INSTANCE);
        for (Object[] objects : a) {
            Arrays.sort(objects, ToStringComparator.INSTANCE);
        }
        return a;
    }

    private Object getAnnotationValue(Annotation annotation, String attributeName) {
        Method method = null;
        boolean acessibleSet = false;
        try {
            method = annotation.annotationType().getDeclaredMethod(attributeName);
            acessibleSet = makeAccessible(method);
            return method.invoke(annotation);
        } catch (Exception ex) {
            return null;
        } finally {
            if (method != null && acessibleSet) {
                method.setAccessible(false);
            }
        }
    }

    private boolean makeAccessible(Method method) {
        if ((!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers()))
                && !method.isAccessible()) {
            method.setAccessible(true);
            return true;
        }
        return false;
    }

    protected static class ToStringComparator implements Comparator<Object> {
        public static final ToStringComparator INSTANCE = new ToStringComparator();
        @Override
        public int compare(Object o1, Object o2) {
            return o1.toString().compareTo(o2.toString());
        }
    }
}
