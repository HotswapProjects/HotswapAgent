[Undertow](http://undertow.io/)
========================================

Configure undertow with extraClasspath, watchResources and webappDir property. Undertow is the web server
of WildFly.

Configuration
-------------
Setup extraClasspath, watchResources or webappDir property in hotswap-agent.properties of the web application
(e.g. WEB-INF/classes/hotswap-agent.properties). Their directories are added to the resource manager of the deployment
and searched before the deployment itself, so changed web resources (HTML, XHTML, CSS, JS, ...) are served without
redeploy. The WAR and the jars in WEB-INF/lib do not need to be exploded.

Use absolute paths, a relative path is resolved against the working directory of the server JVM. To set the project
location only once, define a system property in the server JVM options and use it in the paths:

    -Dproject.dir=/home/devel/myApp

    webappDir=${project.dir}/web/src/main/webapp;${project.dir}/module-a/src/main/resources/META-INF/resources

webappDir accepts more directories separated by ',' or ';'. For web resources packed in a module jar
(META-INF/resources), add the module source directory with these resources.


#### Implementation notes:

