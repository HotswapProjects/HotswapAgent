package org.hotswap.agent.tutorial.printSources;

import org.hotswap.agent.tutorial.framework.PrintSource;

public class HelloPrintSource implements PrintSource {
    @Override
    public String getPrintContent() {
        return "Hello!";
    }
}
