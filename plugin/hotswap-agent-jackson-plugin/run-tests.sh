#!/bin/bash

# fail with first failed test
set -e

# run clean package with all unit tests
function test {
    echo "################################################################"
    echo "########             Running with Jackson $1          ###########"
    echo "################################################################"
    mvn -Djackson.version=$1 clean package
}

test 2.13.0
