#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "Running with OWB $1"
    mvn -Dowb.version=$1 clean package
}

test 1.7.0
test 1.7.5

test 2.0.0
test 2.0.6
