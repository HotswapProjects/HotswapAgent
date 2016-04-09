#!/bin/sh
# simple script to run all Spring versions from 3.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "Running with Hibernate $1"
    mvn -Dresteasy-jaxrs.version=$1 clean package
}

# test following Rest-Easy methods
test 3.0.14.Final
