#!/bin/bash

mvn deploy  --settings ./.travis.settings.xml -DaltDeploymentRepository=sonatype-snapshots::default::https://oss.sonatype.org/content/repositories/snapshots -DskipTests -B
