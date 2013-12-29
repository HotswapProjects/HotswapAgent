Simple webapp
=============

Use this application as a "real world" test scenario.

You need to specify java startup properties:
<pre>java -XXaltjvm=dcevm -javaagent:..\HotswapAgent\target\HotswapAgent.jar</pre>

It is preconfigured with maven jetty plugin, run:
* mvn clean package
* mvn jetty:run
* launch web browser on http://localhost:9091/TestApplication/mvc/test

The application is preconfigured for automatic hotswap deployment (see autoHotswap property in hotswap-agent.properties).
If you specify java startup parameters, all changes to class file will be automatically reloaded:
 <pre>java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000</pre>
If you start the application in debug mode, autoHotswap will be disabled and you should use IDE hotswap feature instead.

To enable debug of Hotswap reload on JVM level, add -XX:TraceRedefineClasses=3 java startup parameter:
<pre>java -XXaltjvm=dcevm -javaagent:..\HotswapAgent\target\HotswapAgent.jar -XX:TraceRedefineClasses=3</pre>
