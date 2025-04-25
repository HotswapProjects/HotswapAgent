>TreasureBoat plugin
====================================

For more information on how to use with TreasureBoat, see the [TreasureBoat wiki page](https://www.treasureboat.org)

It does the following to the TB caches:

- Flushing KVC caches when a class is modified
- Clearing ClassNotFound results from the class cache
- Clearing the component definition cache when a TBComponent is modified
- Clearing the direct action cache when a TBAction (usually TBWAbstractAction) is modified

### Known limitations

- Change of superclass is not supported by DCEVM
- Modifying the return type of a method breaks. It seems there is some cache not cleared.

Configuration
-------------
The is no configuration !



