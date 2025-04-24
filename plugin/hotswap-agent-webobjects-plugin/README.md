WebObjects plugin
====================================

For more information on how to use with WebObjects, see the [WOCommunity wiki page](https://wiki.wocommunity.org/xwiki/bin/view/WOL/Home/WOLips/Using%20DCEVM%20and%20Hotswap%20for%20rapid%20turnaround/)

This plugin replicate the WOJrebel plugin code found in the WOJRebelClassReloadHandler class originally written by qdolan.

It does the following to the WO caches:

- Flushing KVC caches when a class is modified
- Clearing ClassNotFound results from the class cache
- Clearing the component definition cache when a WOComponent is modified
- Clearing the direct action cache when a WOAction (usually DirectAction) is modified

### Known limitations

- Change of superclass is not supported by DCEVM
- Modifying the return type of a method breaks. It seems there is some cache not cleared.

Configuration
-------------
The is no configuration !



