#!/bin/bash

CLASSPATH=bin/production/Irdp:lib/appia-core-4.1.2.jar:lib/log4j-1.2.14.jar:etc/:bin/

java -cp $CLASSPATH irdp.demo.tutorialDA.SampleAppl $@

