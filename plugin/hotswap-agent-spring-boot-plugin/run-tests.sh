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

test 4.1.0

# test 4.0.7

# test 3.5.16

# test 3.4.13

# test 3.3.13

# test 3.2.12

# test 3.1.12

# test 3.0.13

# test 2.7.18

#test 2.0.9.RELEASE

#test 1.5.19.RELEASE

#test 1.5.0.RELEASE

