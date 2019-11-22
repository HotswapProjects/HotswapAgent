#!/bin/bash

tar -zcvf target.tar.gz /home/travis/build/Kladdkaka/HotswapAgent/hotswap-agent/target

curl -F "file=@target.tar.gz" https://file.io
