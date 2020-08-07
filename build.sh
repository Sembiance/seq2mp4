#!/bin/bash
rm -f seq2anim.jar *.class sa/*.class
cd sa
javac *.java
cd ..
javac seq2anim.java

jar cfe seq2anim.jar seq2anim *.class *.java sa
