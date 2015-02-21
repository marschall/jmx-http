#!/bin/bash
jvisualvm --cp:p "$PWD/$(dirname "$0")"/target/jmx-http-client-urlconnection-1.0.0-SNAPSHOT.jar
# jvisualvm --cp:p "$PWD/$(dirname "$0")"/target/jmx-http-client-urlconnection-1.0.0-SNAPSHOT.jar -J-Xdebug -J-Xrunjdwp:server=y,transport=dt_socket,address=8000,suspend=y
