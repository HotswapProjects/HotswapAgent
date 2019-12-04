#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function testOWB1 {
    echo "Running with OWB $1"
    mvn -PCDI1 -Dowb.version=$1 clean compile
    JAVA_HOME=/usr/lib/jvm/java-8-openjdk mvn -Ddcevm.test.arguments=-XXaltjvm=dcevm -Dowb.version=$1 -POWB1 test
}

function testOWB2 {
    echo "Running with OWB $1"
    mvn -POWB22 -Dowb.version=$1 clean package
}

function testWeld2 {
    echo "Running with Weld $1"
    mvn -Dorg.jboss.weld.version=$1 -PWeld clean compile
    JAVA_HOME=/usr/lib/jvm/java-8-openjdk mvn -Ddcevm.test.arguments=-XXaltjvm=dcevm -Dorg.jboss.weld.version=$2 -Dorg.jboss.weld-api.version=$2 -PWeld test
}

function testWeld3 {
    echo "Running with Weld $1"
    mvn -PWeld  -Dorg.jboss.weld.version=$2 -Dorg.jboss.weld-api.version=$2 clean package
}

### start of j11 incompatible versions
testOWB1 1.7.6
testOWB2 2.0.13

testWeld 2.4.8.Final 2.4.SP2
testWeld 3.1.3.Final 3.1.SP2
