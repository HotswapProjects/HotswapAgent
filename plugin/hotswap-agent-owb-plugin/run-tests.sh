#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function testBefore11 {
    echo "Running with OWB $1"
    mvn -Dowb.version=$1 clean compile
    JAVA_HOME=/usr/lib/jvm/java-8-openjdk mvn -Ddcevm.test.arguments=-XXaltjvm=dcevm -Dowb.version=$1 test
}

function test {
    echo "Running with OWB $1"
    mvn -Dowb.version=$1 clean package
}

### start of j11 incompatible versions
testBefore11 1.7.0
testBefore11 1.7.5

testBefore11 2.0.0
### end of j11 incompatible versions

test 2.0.7
test 2.0.8
test 2.0.9
test 2.0.10
test 2.0.11
test 2.0.12
