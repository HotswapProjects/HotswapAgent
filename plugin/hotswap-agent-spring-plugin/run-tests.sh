#!/bin/sh
# simple script to run all Spring versions from 3.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "################################################################"
    echo "########             Running with Spring $1          ###########"
    echo "################################################################"
    mvn -Dorg.springframework.version=$1 clean package
}

# test following Spring versions

test 3.0.1.RELEASE
test 3.0.2.RELEASE
test 3.0.3.RELEASE
test 3.0.4.RELEASE
test 3.0.5.RELEASE
test 3.0.6.RELEASE
test 3.0.7.RELEASE
test 3.1.0.RELEASE
test 3.1.1.RELEASE
test 3.1.2.RELEASE
test 3.1.3.RELEASE
test 3.1.4.RELEASE

# Spring 3.2 issues with Java 1.8
# see http://stackoverflow.com/questions/22526695/java-1-8-asm-classreader-failed-to-parse-class-file-probably-due-to-a-new-java
if [[ $JAVA_HOME != *1.8* ]]
then
    test 3.2.0.RELEASE
    test 3.2.1.RELEASE
    test 3.2.2.RELEASE
    test 3.2.3.RELEASE
    test 3.2.4.RELEASE
    test 3.2.5.RELEASE
    test 3.2.6.RELEASE
    test 3.2.7.RELEASE
    test 3.2.8.RELEASE
    test 3.2.9.RELEASE
fi

test 4.0.0.RELEASE
test 4.0.1.RELEASE
test 4.0.2.RELEASE
test 4.0.3.RELEASE
test 4.0.4.RELEASE
test 4.0.5.RELEASE
test 4.0.6.RELEASE
test 4.0.7.RELEASE
test 4.0.8.RELEASE
test 4.0.9.RELEASE

test 4.1.0.RELEASE
test 4.1.1.RELEASE
test 4.1.2.RELEASE
test 4.1.3.RELEASE
test 4.1.4.RELEASE
test 4.1.5.RELEASE
test 4.1.6.RELEASE
test 4.1.7.RELEASE
test 4.1.8.RELEASE
test 4.1.9.RELEASE

test 4.2.0.RELEASE
test 4.2.1.RELEASE
test 4.2.2.RELEASE
test 4.2.3.RELEASE
test 4.2.4.RELEASE
test 4.2.5.RELEASE
test 4.2.6.RELEASE

test 4.3.0.RELEASE
test 4.3.1.RELEASE
test 4.3.2.RELEASE
test 4.3.3.RELEASE


