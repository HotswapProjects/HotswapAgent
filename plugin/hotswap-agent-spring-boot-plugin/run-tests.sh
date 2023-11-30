#!/bin/bash
# simple script to run all Spring Boot versions from 1.5.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "################################################################"
    echo "########             Running with Spring $1          ###########"
    echo "################################################################"
    mvn -Dorg.springframework.boot.version=$1 clean package -e
}

# test following SpringBoot versions

test 2.7.16

#test 2.0.9.RELEASE

#test 1.5.19.RELEASE

#test 1.5.0.RELEASE

