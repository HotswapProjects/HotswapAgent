#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

function testCDI2 {
    echo "Running with OWB $1"
    mvn -Dowb.version=$1 clean package
}

testCDI2 4.0.2
