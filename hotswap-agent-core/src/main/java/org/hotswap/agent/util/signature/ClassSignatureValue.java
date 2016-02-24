package org.hotswap.agent.util.signature;

/**
 * The Class ClassSignatureValue.
 *
 * @author Erki Ehtla, Vladimir Dvorak
 */
public class ClassSignatureValue {
    private String value;

    public ClassSignatureValue(String value) {
        this.value = value;
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (o == null || !(o instanceof ClassSignatureValue))
            return false;
        return value.equals(((ClassSignatureValue)o).value);
    }

    public String toString() {
        return value;
    }

}
