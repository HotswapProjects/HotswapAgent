[Velocity](https://velocity.apache.org/) plugin
=================
1. Specifying extraClasspath in hotswap-agent.properties (including a folder named $$ha$velocity containing velocity 
files)  
2. Hot deployment can be achieved without rebuilding the project in IDEA, see https://github.com/spring-projects/spring-boot/issues/34 for details
#### Implementation notes

* Patch org.springframework.ui.velocity.VelocityEngineFactory#setPreferFileSystemAccess by adding the following code snippet:
    ```java
	public void setPreferFileSystemAccess(boolean preferFileSystemAccess) {
		...
        this.preferFileSystemAccess = false;
	}
  
  	public void setResourceLoaderPath(String resourceLoaderPath) {
		...
        this.resourceLoaderPath = "classpath:/$$ha$velocity/," + this.resourceLoaderPath;
	}
  
  	protected void initSpringResourceLoader(VelocityEngine velocityEngine, String resourceLoaderPath) {
        ...
        velocityEngine.setProperty("spring.resource.loader.cache", "false");
	}
  
   ```
Please note, this make project use SpringResourceLoader instead of the default FileResourceLoader to get velocity 
template and its cache is set closed. It is strongly recommended not to run in an online environment.