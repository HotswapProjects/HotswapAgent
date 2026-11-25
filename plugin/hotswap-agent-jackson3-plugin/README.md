[Jackson 3 Plugin](https://github.com/FasterXML/jackson)
==========

Jackson3Plugin clears the caches of Jackson 3 (`tools.jackson`) when a class is redefined, so that the serializers
and deserializers of the class are created again, e.g. after a field or a record component is added. Spring Boot 4
uses Jackson 3.

Like the [Jackson plugin](../hotswap-agent-jackson-plugin/README.md) for Jackson 2 (`com.fasterxml.jackson`), it
registers the caches when they are created: the serializer and deserializer caches with their read-only copies, the
type caches, the root deserializers of the mappers and every `SimpleLookupCache`, e.g. of the root names. It clears
them right after the classes are redefined and once more a second later, for what a request running meanwhile cached
again. A reader or writer keeps the caches of its mapper, so they are cleared also after the mapper is garbage
collected.

Not cleared:

* a reader or writer created for a type (`readerFor`, `writerFor`, `forType`), a `SequenceWriter` or
  a `MappingIterator`, kept e.g. in a field: it keeps the (de)serializer it looked up when it was created,
* the root deserializers of a reader whose mapper was garbage collected.

The tests need Java 17, they are skipped on older versions.
