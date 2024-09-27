#!/bin/bash
# simple script to run all Spring versions from 3.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "################################################################"
    echo "########             Running with MybatisPlus $1          ###########"
    echo "################################################################"
    mvn -Dorg.mybatis.plus.version=$1 clean package -e
}

test 3.5.7
# test 3.5.6
# test 3.5.5
# test 3.5.4
# test 3.5.3
# test 3.5.2
test 3.5.1
# test 3.4.3.4
# test 3.4.3
# test 3.4.2
# test 3.4.1
test 3.4.0
test 3.3.0
test 3.2.0
# test 3.1.0
# test 3.0.1
