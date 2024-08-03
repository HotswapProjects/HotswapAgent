#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function testOWB4 {
    echo "Running with OWB $1"
    mvn -POWB22 -Dowb.version=$1 clean package
}

function testWeld5 {
    echo "Running with Weld $1"
    mvn -PWeld  -Dorg.jboss.weld.version=$2 -Dorg.jboss.weld-api.version=$2 clean package
}

### start of j11 incompatible versions
testOWB2 4.0.2

testWeld 5.1.0.Final 5.0.SP3
