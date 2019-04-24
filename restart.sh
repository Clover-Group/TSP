#!/bin/bash

rm -f logs/*

env JAVA_OPTS="${TSP_JAVA_OPTS:--Xms512M -Xmx4G}" \
    JAVA_TOOL_OPTIONS="${TSP_JAVA_TOOL_OPTS:--Dfile.encoding=UTF8}" \
    sbt "http/runMain ${TSP_LAUNCHER:-ru.itclover.tsp.http.Launcher} ${TSP_LAUNCHER_ARGS:-flink-local}"
