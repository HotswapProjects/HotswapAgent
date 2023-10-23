#!/bin/sh
# simple script to run all tests and all versions. It fails with first failure
# this should be replaced by build sever in the future

# fail with first failed test
#set -e

function test {
    echo "################################################################"
    echo "################################################################"
    echo "#####                 Running with Java $1"         ############"
    echo "################################################################"
    echo "################################################################"

    export JAVA_HOME=$1

    echo "Resolved version: " `"$JAVA_HOME/bin/java" -XXaltjvm=dcevm -version` || echo "$1 is not a valid Java installation with DCEVM."

    mvn clean install -DskipTests

    # run tests for different versions
    cd plugin/hotswap-agent-spring-plugin; ./run-tests.sh; cd ../..
#    cd plugin/hotswap-agent-hibernate-plugin; ./run-tests.sh; cd ../..
#    cd plugin/hotswap-agent-hibernate3-plugin; ./run-tests.sh; cd ../..
#    cd plugin/hotswap-agent-weld-plugin; ./run-tests.sh; cd ../..
#    cd plugin/hotswap-agent-owb-plugin; ./run-tests.sh; cd ../..
#    cd plugin/hotswap-agent-deltaspike-plugin; ./run-tests.sh; cd ../..
#    cd plugin/hotswap-agent-resteasy-registry-plugin; ./run-tests.sh; cd ../..
}

#test "c:\Program Files\Java\jdk1.7.0_45"
test "/Library/Java/JavaVirtualMachines/dcevm-11.0.15+1/Contents/Home"
