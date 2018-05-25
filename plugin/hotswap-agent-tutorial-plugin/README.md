Hotswap Agent Plugin Tutorial 
=============================
Tutorial explaining how to implement plugin for a framework.

Run `org.hotswap.agent.tutorial.TryMe` main class and create/modify classes
in `org.hotswap.agent.tutorial.printSources` playground package to see changes instantly.

You need to set javaagent, i.e. `java -javaagent:hotswap-agent-core.jar org.hotswap.agent.tutorial.TryMe`.

Printer Framework
-----------------
To create a plugin for a framework we need first the framework :-). For this purpose 
there is very simple `org.hotswap.agent.tutorial.framework.PrinterService` 
printing service. It scans for PrintSource classes and prints periodically
the content of each PrintSource to PrintTarget. The framework uses caching. 

Printer Plugin
--------------
Our great printer service works nicely, however it loads whole setup on startup
and does not change printing output if anything changes. Here comes Hotswap
Agent Plugin with rescue. 

What we need to reload?
* Flush the `PrinterService` cache whenever any `PrintSource` is hotswapped (changed)
* Reload `PrinterService` configuration after the `printer.properties` is changed
* Watch `PrintSource` directory for new `.class` files (Printer framework scans)
    the files only on startup time. New class is event not known to the classloader.
    
If you have access to the framework sources, it is always easier to modify 
 
