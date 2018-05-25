package org.hotswap.agent.tutorial.framework;

import org.hotswap.agent.javassist.bytecode.ClassFile;
import org.hotswap.agent.util.scanner.ClassPathScanner;
import org.hotswap.agent.util.scanner.ScannerVisitor;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Simulate classpath scanning mechanism as is provided by many frameworks (JEE, Spring, Hibernate, ...)
 *
 * Our example Printer framework will read sourcePackage property from printer.properties file
 * and scans for all classes with PrintSource interface in this package.
 *
 * This scan is done on startup and any modification of printer.properties need restart. New classes after
 * scan are not discovered. This is where Hotswap Agent plugin helps - get all changes on the fly.
 */
public class PrinterSourceScanner {
    /**
     * Configuration file well-known location (src/main/resources).
     * This is similar to META-INF/persistence.xml for JPA.
     */
    public static final String PRINTER_PROPERTY_FILE = "printer.properties";

    /**
     *
     *
     * @return list of discovered sources
     * @throws IOException unable to read configuration file or classes in sourcePackage
     */
    public List<PrintSource> scanPrintSources() throws IOException {

        // load the printer framework configuration file
        Properties printerProps = new Properties();
        printerProps.load(getClass().getClassLoader().getResourceAsStream(PRINTER_PROPERTY_FILE));

        // read the configuration property
        String sourcePackage = printerProps.getProperty("sourcePackage", "org.hotswap.agent.example.printerApp");

        // and do the work
        return scanPackageForPrintSources(sourcePackage);
    }

    // This method uses Hotswap Agent scanner and javaasist for simplicity (it is already on classpath)
    // standard framework usually has custom methods to scan files.
    private List<PrintSource> scanPackageForPrintSources(String sourcePackage) throws IOException {
        final List<PrintSource> discoveredSources = new ArrayList<>();
        String sourcePath = sourcePackage.replace(".", "/");

        new ClassPathScanner().scan(getClass().getClassLoader(), sourcePath, new ScannerVisitor() {
            @Override
            public void visit(InputStream file) throws IOException {
                ClassFile cf;
                try {
                    DataInputStream dstream = new DataInputStream(file);
                    cf = new ClassFile(dstream);

                    for (String iface : cf.getInterfaces()) {
                        if (iface.equals(PrintSource.class.getName())) {
                            //noinspection unchecked
                            Class<PrintSource> printSource = (Class<PrintSource>) getClass().getClassLoader().loadClass(cf.getName());
                            discoveredSources.add(printSource.newInstance());
                        }
                    }
                } catch (IOException e) {
                    throw new IOException("Stream not a valid classFile", e);
                } catch (ClassNotFoundException e) {
                    throw new IOException("Discovered class not found by classloader", e);
                } catch (IllegalAccessException e) {
                    throw new IOException("Unable to create new instance", e);
                } catch (InstantiationException e) {
                    throw new IOException("Print source does not contain no-arg constructor", e);
                }

            }
        });

        return discoveredSources;
    }
}
