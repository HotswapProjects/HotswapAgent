package org.hotswap.agent.tutorial.plugin;

import org.hotswap.agent.annotation.*;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.tutorial.framework.PrintSource;
import org.hotswap.agent.tutorial.framework.PrinterService;
import org.hotswap.agent.tutorial.framework.PrinterSourceScanner;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hotswap.agent.annotation.LoadEvent.REDEFINE;

/**
 * Refresh configuration and caches of PrinterService.
 */
@Plugin(name = "Printer Plugin", description = "Listen to any redefinition and refresh cache.",
        testedVersions = "1.x")
public class PrinterHAPlugin {

    // the printerService instance
    PrinterService printerService;

    /**
     * Application should create and inicialize plugin instance
     *
     * @param printerService the printerService instance
     */
    public PrinterHAPlugin(PrinterService printerService) {
        this.printerService = printerService;
    }

    /**
     * Plugin should be started from a application framework class on startup.
     *
     * @param printer the printerService instance
     */
    public static void register(PrinterService printer) {
        PluginManager.getInstance().getPluginRegistry().initializePluginInstance(new PrinterHAPlugin(printer));
    }


    /**
     * Hotswap Agent scheduling service, see javadoc for usage. See @{@link Init} annotation javadoc
     * for list of supported services.
     */
    @Init
    Scheduler scheduler;

    /**
     * Call framework class HelloWorldService, typical usage is invalidate caches.
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = REDEFINE)
    public void reloadClass(CtClass clazz) throws NotFoundException {
        if (isPrintSource(clazz)) {
            // refresh the printerService cache after a class is redefined
            // use scheduler to run the refresh AFTER the class is replaced in classloader
            // TODO - create @AfterClassLoadEvent in HA core to simplify
            scheduler.scheduleCommand(() -> printerService.refresh());
        }
    }

    /**
     * New class file on classpath.
     *
     * @param clazz javaassist class, please note that this class is not yet loaded by any classloader
     */
    @OnClassFileEvent(classNameRegexp = ".*", events = FileEvent.CREATE)
    public void createClass(CtClass clazz) throws NotFoundException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        // check it is new PrintSource.
        // We should check that the new class in in correct package according to configuraion,
        // but this is ommited for simplicity
        if (isPrintSource(clazz)) {
            // load the new class using framework classloader
            //noinspection unchecked
            Class<PrintSource> newClass = (Class<PrintSource>) printerService.getClass().getClassLoader().loadClass(clazz.getName());
            PrintSource newPrintSource = newClass.newInstance();

            // and register it
            printerService.addPrintSource(newPrintSource);
            // because it did not went through standard autoscan process, register it manually
            autoDiscoveredPrintSources.add(newPrintSource);

            // and invalidate caches
            printerService.refresh();
        }
    }


    /////////////////////////////////////////////////////////////////////////
    // HACKING section
    // Let's simulate some hacking that is needed if the framework is unaware
    // of hotswap agent plugin.
    // Use this approach only if you cannot or do not want to modify framework
    // classes to be more "reload" friendly.
    /////////////////////////////////////////////////////////////////////////

    /**
     * Reload Printer configuration after the property file changes.
     */
    @OnResourceFileEvent(path = PrinterSourceScanner.PRINTER_PROPERTY_FILE, events = FileEvent.MODIFY)
    public void reloadConfiguration() throws IOException {
        // get printer internal printSources list and exchange old to new
        //noinspection unchecked
        List<PrintSource> currentSource = (List<PrintSource>) ReflectionHelper.get(printerService, "printSources");

        // we should remove only previously autodiscovered printers, not the manually added ones.
        currentSource.removeAll(autoDiscoveredPrintSources);

        // use scanner to load new configuration
        //  modified scanPrintSources method will set autoDiscoveredPrintSources to newly discovered list.
        currentSource.addAll(new PrinterSourceScanner().scanPrintSources());

        // refresh cache
        printerService.refresh();
    }


    // store currently discovered and used printSources. We will need them for reload to remove them
    // before new scan.
    List<PrintSource> autoDiscoveredPrintSources = new ArrayList<>();

    /**
     * Called from modified PrinterSourceScanner.scanPrintSources method to store autodiscovered printSources
     *
     * @param autoDiscoveredPrintSources currently autodiscovered sources
     */
    public void setAutoDiscoveredPrintSources(List<PrintSource> autoDiscoveredPrintSources) {
        this.autoDiscoveredPrintSources = autoDiscoveredPrintSources;
    }

    /**
     * Happy hacking :)
     * This is really needed only if you cannot modify framework sources to be more "reload friendly"
     * and you need to hook into framework execution.
     *
     * @param clazz javaassist class file, which you can modify before it's loaded.
     *              See @{link OnClassLoadEvent} javadoc to see available parameters.
     * @throws CannotCompileException error in new method definition
     */
    @OnClassLoadEvent(classNameRegexp = "org.hotswap.agent.example.framework.PrinterSourceScanner")
    public void register(CtClass clazz) throws CannotCompileException {
        // create java source code to call PrinterHAPlugin.setAutoDiscoveredPrintSources() method
        String callbackMethod = PluginManagerInvoker.buildCallPluginMethod(PrinterHAPlugin.class,
                "setAutoDiscoveredPrintSources", "$_", "java.util.List");

        // search the PrinterSourceScanner for existing scanPrintSources method
        CtMethod scanPrintSourcesMethod = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().equals("scanPrintSources"))
                .findFirst().orElseThrow(IllegalStateException::new);

        // and insert new code just before return statement
        scanPrintSourcesMethod.insertAfter(callbackMethod);
    }

    // check if the class implements PrintSource interface
    // usually you will check interface or annotation
    // (e.g. AnnotationHelper.hasAnnotation(original, "javax.ws.rs.Path"))
    private boolean isPrintSource(CtClass clazz) throws NotFoundException {
        return Arrays.stream(clazz.getInterfaces())
                .anyMatch(i -> i.getName().equals(PrintSource.class.getName()));
    }
}
