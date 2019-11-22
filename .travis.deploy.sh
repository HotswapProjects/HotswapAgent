#!/bin/bash

tar -zcvf target.tar.gz target

curl -F "file=@target.tar.gz" https://file.io
