[Log4j2](http://logging.apache.org/log4j/2.x/)
====================================
Log4j2 configuration reload.

#### Implementation notes:
Plugin hooks into `org.apache.logging.log4j.core.LoggerContext.setConfiguration(Configuration)` call. All configuration URL's are
registered and watched for change. After the file change, full reconfiguration via setConfigLocation(URI) method is executed.


