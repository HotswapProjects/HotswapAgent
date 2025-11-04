package org.hotswap.agent.annotation;

import java.lang.annotation.*;

/**
 * Indicates that the visibility of a field, method, or constructor
 * has been relaxed to make the code testable.
 * <p>
 * This annotation serves purely as documentation — it does not change
 * visibility or behavior at runtime. It helps other developers understand
 * that a particular element is exposed for testing purposes and should not
 * be used by production code directly.
 * </p>
 *
 * <p><b>Typical usage:</b></p>
 * <pre>{@code
 * class MyService {
 *
 *     @VisibleForTesting
 *     void computeInternalLogic() {
 *         // Exposed only for test visibility
 *     }
 * }
 * }</pre>
 *
 * <p><b>Best practice:</b></p>
 * Keep such methods <i>package-private</i> and place test classes
 * in the same package so that tests can access them.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface TestOnly {
    /**
     * Optional note describing why this element is visible for testing.
     *
     * @return explanation or context for the increased visibility
     */
    String value() default "";
}
