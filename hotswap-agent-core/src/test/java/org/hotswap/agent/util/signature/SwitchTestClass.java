package org.hotswap.agent.util.signature;

public class SwitchTestClass {
    public int methodWithSwitch(EnumSwitchTest val) {
        switch(val) {
        case SW_1:
            return 1;
        case SW_2:
            return 2;
        }
        return 0;
    }
}
