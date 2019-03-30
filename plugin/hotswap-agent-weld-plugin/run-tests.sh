#!/bin/sh
# simple script to run all Weld versions from 2.0 up to latest.
# this should be replaced by build sever in the future

# fail with first failed test
set -e

# run clean package with all unit tests
function test2 {
    echo "Running with OWB $1"
    mvn -Dowb.version=$1 clean compile
    JAVA_HOME=/usr/lib/jvm/java-8-openjdk mvn -Ddcevm.test.arguments=-XXaltjvm=dcevm -Dorg.jboss.weld.version=$1 -PWeld2 clean package test
}

function test3 {
    echo "Running with Weld $1"
    mvn -Dorg.jboss.weld.version=$1 -PWeld3 clean package
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

test2 2.2.5.Final
test2 2.2.6.Final
test2 2.2.7.Final
test2 2.2.8.Final
test2 2.2.9.Final
test2 2.2.10.Final
test2 2.2.11.Final
test2 2.2.12.Final
test2 2.2.13.Final
test2 2.2.14.Final
test2 2.2.15.Final
test2 2.2.16.Final

test2 2.3.0.Final
test2 2.3.1.Final
test2 2.3.2.Final
test2 2.3.3.Final
test2 2.3.4.Final
test2 2.3.5.Final

test2 2.4.0.Final
test2 2.4.1.Final
test2 2.4.2.Final
test2 2.4.3.Final
test2 2.4.4.Final
test2 2.4.5.Final
test2 2.4.6.Final
test2 2.4.7.Final
test2 2.4.8.Final

test3 3.0.0.Final
test3 3.0.1.Final
test3 3.0.2.Final
test3 3.0.3.Final
test3 3.0.4.Final
test3 3.0.5.Final

test3 3.1.0.Final

