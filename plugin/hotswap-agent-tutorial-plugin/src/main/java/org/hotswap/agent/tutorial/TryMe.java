package org.hotswap.agent.tutorial;

import org.hotswap.agent.tutorial.framework.PrinterService;

/**
 * Playground to test Hotswap Agent capabilities.
 *
 * Run this class in your IDE with DCEVM JVM and modify sources
 * in {@link org.hotswap.agent.tutorial.printSources} directory -
 * add new Class implementing {@link org.hotswap.agent.tutorial.framework.PrintSource} interface
 * or modify HelloPrintSource (even add/modify fields/methods, lambdas, inner classes, ...)
 * and see standard output reflecting your changes.
 */
public class TryMe {

    public static void main(String[] args) throws InterruptedException {
        PrinterService printerService = new PrinterService();
        printerService.run();
        Thread.sleep(1000000);
    }
}
