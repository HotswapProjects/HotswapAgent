AnonymousClassPatch
====================
This is a patch to resolve issues with anonymous classes hotswap.

If you reload a class MyClass with two anonymous classes, class names MyClass$1, MyClass$2 are created
in the order as anonymous class appears in the source code.
After anonymous class insertion/deletion the indexes are shifted producing not compatible hot swap
(change of superclass).

This patch will create class state info before the change (from current ClassLoader via reflection) and
after the change (from filesystem using javassist) find all compatible transitions.

Known issues
---------------
For correct plugin functionality the modified class file must be uploaded to destination classloader before
redefinition is processed. It is no problem if application shares the same directory with target folder of
IDE compiler. But it can be problem if someone debug application inside servlet container. In that case
application should publish modified classes before the redefinition is called via debugger. It can't be
alway accomplished by IDE settingis, in that cases it is better to disable this plugin at all.

Example
-------
For example if you exchange order the anonymous class appears in the source code, Transition may
produce something like:

* MyClass$1 -> MyClass$2
* MyClass$2 -> MyClass$3
* MyClass$3 -> MyClass$1


Then the transformation will behave:

* When the class MyClass$1 is hot swapped, the bytecode from MyClass$2 is returned (and renamed to MyClass$1)
* When the class MyClass$2 is hot swapped, the bytecode from MyClass$3 is returned (and renamed to MyClass$2)
* When the class MyClass$3 is hot swapped, the bytecode from MyClass$1 is returned (and renamed to MyClass$3)
* When the class MyClass is hot swapped, all occurences of MyClass$1 are exchanged for MyClass$3
                          , all occurences of MyClass$2 are exchanged for MyClass$1
                          , all occurences of MyClass$3 are exchanged for MyClass$2


Incompatible changes
----------------------
Swap may produce  not compatible change. Consider existing MyClass$1 and MyClass$2, then MyClass$1
is removed. Then hotswap is called only on MyClass$1, which contains different class to MyClass$2. Then
MyClass$1 is on hotswap replaced with empty implementation and new class MyClass$1000 is created to
contain code from the new MyClass$1 (class compatible with old MyClass$2). Note that because this is not
a true hotswap, old existing instances of MyClass$1 are updated to an empty class, not the new one.
When calling a method on this class, AbstractErrorMethod is thrown (this should be replaced to some
more clear error in the future).

ClassInit
====================
Initialize new static members. When a class is redefined, DCEVM copies values from all surviving static members
to the new class, but it leaves all new static members uninitialized. That applies to enumerations as well. ClassInit
plugin fixes this issue. After redefinition all surviving static values are kept and initialization is done
only for new static members. After redefinition of an enumeration all surviving enumeration values are kept,
so it is guaranteed that surviving enumeration value Enum.X' is identical to value before redefinition Enum.X.

Known issues
---------------
Ordinal values of a redefined enumeration value X'.ordinal() values can be different than original one

    X.ordinal() != X'.ordinal()

#### Implementation notes:
Content of '<clinit>' method is copied to '__ha_clinit'. Assignment to surviving members are removed using javassist.
'__ha_clinit' method is called after class is redefined in JVM after timeout (100ms).
