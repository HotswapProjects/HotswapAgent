#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function testWeld2 {
    echo "Running with Weld $1"
    mvn -Dorg.jboss.weld-se.version=$1 -PWeld2 clean compile
    JAVA_HOME=/usr/lib/jvm/java-8-openjdk mvn -Ddcevm.test.arguments=-XXaltjvm=dcevm -Dorg.jboss.weld.version=$1 -PWeld2 test
}

function testWeld3 {
    echo "Running with Weld $1 $2"
    mvn -Dorg.jboss.weld.version=$1 -Dorg.jboss.weld-spi.version=$2 -PWeld3 clean package
}

# testWeld3 3.1.7.Final 3.1.SP1
