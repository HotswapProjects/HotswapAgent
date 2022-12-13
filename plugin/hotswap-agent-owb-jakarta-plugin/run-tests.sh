#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

function testCDI2 {
    echo "Running with OWB $1"
    mvn -PCDI2 -Dowb.version=$1 clean package
}

### Run tests CDI2 on j11
testCDI2 2.0.27
