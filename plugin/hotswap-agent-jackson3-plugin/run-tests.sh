#!/bin/bash

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "################################################################"
    echo "########             Running with Jackson $1          ###########"
    echo "################################################################"
    # the tests are skipped before Java 17, fail instead
    mvn -Djackson3.version=$1 -Dmaven.test.skip=false clean package
}

test 3.0.1
test 3.1.7
test 3.2.0
