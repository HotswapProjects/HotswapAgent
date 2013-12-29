This value should be replaced in target/classes, but not replaced in src/main/resources.
Use this example to test watchResources configuration property. Before the file is changed, it loads
value from target/classes and should replace the value with application name. After the file is changed,
 it is served from src/main/resources and it is not replaced.

VALUE: '${name}'