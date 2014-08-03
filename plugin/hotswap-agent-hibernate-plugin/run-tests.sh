#!/bin/sh
# simple script to run all Spring versions from 3.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "Running with Hibernate $1"
    mvn -Dorg.hibernateframework.version=$1 clean package
}

# test following Hibernate versions
# test 4.0.0.Final
test 4.0.1.Final
test 4.1.0.Final
test 4.1.1.Final
test 4.1.2.Final
test 4.1.3.Final
test 4.1.4.Final
test 4.1.5.Final
test 4.1.6.Final
test 4.1.7.Final
test 4.1.8.Final
test 4.1.9.Final
test 4.1.10.Final
test 4.1.11.Final
test 4.1.12.Final
test 4.2.1.Final
test 4.2.2.Final
test 4.2.3.Final
test 4.2.4.Final
test 4.2.5.Final
test 4.2.6.Final
test 4.2.7.Final
test 4.2.8.Final
test 4.2.9.Final
test 4.2.10.Final
test 4.2.11.Final
test 4.2.12.Final
test 4.2.13.Final
test 4.3.1.Final
test 4.3.2.Final
test 4.3.3.Final
test 4.3.4.Final
test 4.3.5.Final

