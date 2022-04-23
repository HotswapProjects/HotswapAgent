#!/bin/bash
# simple script to run all Spring versions from 3.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "################################################################"
    echo "########             Running with Spring $1          ###########"
    echo "################################################################"
    mvn -Dorg.springframework.version=$1 clean package
}

# test following Spring versions

# test 4.3.0.RELEASE
# test 4.3.1.RELEASE
# test 4.3.2.RELEASE
# test 4.3.3.RELEASE
# test 4.3.4.RELEASE
# test 4.3.5.RELEASE
# test 4.3.6.RELEASE
# test 4.3.7.RELEASE
# test 4.3.8.RELEASE
# test 4.3.9.RELEASE
# test 4.3.10.RELEASE
# test 4.3.11.RELEASE
# test 4.3.12.RELEASE
# test 4.3.13.RELEASE

# 4.3.20 is lowest not vulnerable version(2022.01.19) look at CVE-2018-15756
test 4.3.20.RELEASE

test 5.0.0.RELEASE
test 5.0.1.RELEASE
test 5.0.2.RELEASE

test 5.1.0.RELEASE
test 5.1.1.RELEASE
test 5.1.2.RELEASE
test 5.1.3.RELEASE
test 5.1.4.RELEASE
test 5.1.5.RELEASE
test 5.1.6.RELEASE
test 5.1.7.RELEASE
test 5.1.8.RELEASE
test 5.1.9.RELEASE
test 5.1.10.RELEASE
test 5.1.11.RELEASE

test 5.2.0.RELEASE
test 5.2.1.RELEASE
test 5.2.6.RELEASE
test 5.2.7.RELEASE
test 5.2.8.RELEASE
test 5.2.9.RELEASE
test 5.2.10.RELEASE
test 5.2.11.RELEASE
test 5.2.12.RELEASE
test 5.2.13.RELEASE
test 5.2.14.RELEASE
test 5.2.15.RELEASE
test 5.2.16.RELEASE
test 5.2.17.RELEASE
test 5.2.18.RELEASE
test 5.2.19.RELEASE
test 5.2.20.RELEASE
test 5.2.21.RELEASE
