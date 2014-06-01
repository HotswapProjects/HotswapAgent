#!/bin/sh
# simple script to run all Spring versions from 3.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "Running with Spring $1"
    mvn -Dorg.hibernateframework.version=$1 clean package
}

# test this Spring versions
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
test 4.0.0.RELEASE
test 4.0.1.RELEASE
test 4.0.2.RELEASE
test 4.0.3.RELEASE
test 4.0.4.RELEASE
test 4.0.5.RELEASE

