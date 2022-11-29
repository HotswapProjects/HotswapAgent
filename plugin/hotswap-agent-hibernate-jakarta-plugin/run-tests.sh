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
test 4.3.6.Final
test 4.3.7.Final
test 4.3.8.Final
test 4.3.9.Final
test 4.3.10.Final
test 4.3.11.Final

# 5.0.0-5.0.9 are failing on j11
#test 5.0.0.Final
#test 5.0.1.Final
#test 5.0.2.Final
#test 5.0.3.Final
#test 5.0.4.Final
#test 5.0.5.Final
#test 5.0.6.Final
#test 5.0.7.Final
#test 5.0.8.Final
#test 5.0.9.Final
test 5.0.10.Final
test 5.0.11.Final
test 5.1.0.Final
test 5.1.1.Final
test 5.2.1.Final
test 5.2.2.Final
test 5.2.3.Final
test 5.2.4.Final
test 5.2.5.Final
test 5.2.6.Final
test 5.2.7.Final
test 5.2.8.Final
test 5.2.9.Final
test 5.2.10.Final

# test 5.3.0.Final # 5.3.0 is broken on j11
test 5.3.1.Final
test 5.3.2.Final
test 5.3.3.Final
test 5.3.4.Final
test 5.3.5.Final
test 5.3.6.Final
test 5.3.7.Final
test 5.3.8.Final
test 5.3.9.Final
test 5.3.10.Final
test 5.3.11.Final
test 5.3.12.Final
test 5.3.13.Final
test 5.3.14.Final

test 5.4.0.Final
test 5.4.1.Final
test 5.4.2.Final
test 5.4.3.Final
test 5.4.4.Final
test 5.4.5.Final
test 5.4.6.Final
test 5.4.7.Final
test 5.4.8.Final
test 5.4.9.Final
test 5.4.10.Final
test 5.4.11.Final
test 5.4.12.Final
test 5.4.13.Final
test 5.4.14.Final
