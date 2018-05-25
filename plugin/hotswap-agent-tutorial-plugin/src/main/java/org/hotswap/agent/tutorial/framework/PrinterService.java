package org.hotswap.agent.tutorial.framework;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple printer service. It reads the content from all {@link PrintSource}s and
 * write list of all contents to {@link PrintTarget} in infinite loop.
 *
 * Register PrintSource either manually with addPrintSource() or use autodiscovery
 * mechanism with printer.properties configuration.
 */
public class PrinterService {

    // list of all autodiscovered or added sources
    private List<PrintSource> printSources = new ArrayList<>();
    // you may want to setup target where to print (default is System.out)
    private PrintTarget printTarget = new PrintTarget() {};
    // frameworks usually cache some content to improve performance, simulate this with this content cache
    private List<String> cachedContents = new ArrayList<>();

    // simple print method, just print list of contents
    private void print() {
        printTarget.print("Contents: " + Arrays.toString(cachedContents.toArray()));
    }

    // because we cache the content, we need a method to refresh it
    public void refresh() {
        cachedContents = printSources.stream()
                .map(PrintSource::getPrintContent)
                .collect(Collectors.toList());
    }

    /**
     * Run the printer framework.
     */
    public void run()  {
        // register HA plugin at startup time
        registerHaPlugin();

        // use scanner to load print sources according to configuration.
        try {
            printSources = new PrinterSourceScanner().scanPrintSources();
            refresh();
        } catch (IOException e) {
            throw new IllegalStateException("Wrong configuration, unable to scan for print sources", e);
        } catch (NoClassDefFoundError e) {
            //   note that this tutorial will not work without hotswap-agent javaagent, because it
            //   uses ha-core classes for framework stuff. However, this is not
            //   the case for standard framework and missing hotswap-agent-core should
            //   be handled - see registerHaPlugin()
            throw new Error("Hotswap Agent classes not found. " +
                    "Please run java with '-javaagent:hotswap-agent-core.jar' switch.");
        }

        // do the printing
        startWorkingThread();
    }

    // Init the plugin with this service. Usually only one plugin instance is needed for a framework
    // This is the only place where you call plugin from your framework. If you write a plugin for
    // framework where you cannot or do not want to modify framework sources, this can be done via
    // runtime bytecode manipulation from the plugin itself
    private void registerHaPlugin() {
        try {
            org.hotswap.agent.tutorial.plugin.PrinterHAPlugin.register(this);
        } catch (NoClassDefFoundError e) {
            // Ok, hotswap not available
        }
    }

    /**
     * Stop the printer framework.
     */
    public void stop() {
        stopped = true;
    }

    /**
     * Do the printing.
     *
     * It will just write to PrintTarget in infinite loop list of all print contents.
     */
    private void startWorkingThread() {
        new Thread(() -> {
            while (true) {
                try {
                    if (stopped) {
                        break;
                    }
                    print();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    // flag to stop working thread
    private boolean stopped;

    /**
     * Add manually print source.
     *
     * @param printSource the source
     */
    public void addPrintSource(PrintSource printSource) {
        printSources.add(printSource);
        refresh();
    }

    /**
     * Change print target.
     *
     * @param printTarget
     */
    public void setPrintTarget(PrintTarget printTarget) {
        this.printTarget = printTarget;
    }

}
