#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "Running with Weld $1"
    mvn -Dorg.jboss.weld.version=$1 clean package
}

# 2.0.x - unsupported
# test 2.0.0.Final
# test 2.0.1.Final
# test 2.0.2.Final
# test 2.0.3.Final
# test 2.0.4.Final
# test 2.0.1.Final

# 2.1.x - unsupported
# test 2.1.0.Final
# test 2.1.1.Final
# test 2.1.2.Final

# test 2.2.0.Final
# test 2.2.1.Final
# test 2.2.2.Final
# test 2.2.3.Final
# test 2.2.4.Final
test 2.2.5.Final
test 2.2.6.Final
test 2.2.7.Final
test 2.2.8.Final
test 2.2.9.Final
test 2.2.10.Final
test 2.2.11.Final
test 2.2.12.Final
test 2.2.13.Final
test 2.2.14.Final
test 2.2.15.Final
test 2.2.16.Final

test 2.3.0.Final
test 2.3.1.Final
test 2.3.2.Final
test 2.3.3.Final
test 2.3.4.Final
test 2.3.5.Final

test 2.4.0.Final
