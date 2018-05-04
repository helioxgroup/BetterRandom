#!/bin/sh
if [ "$ANDROID" = 1 ]; then
  MAYBE_ANDROID_FLAG="-Pandroid"
else
  MAYBE_ANDROID_FLAG=""
fi
if [ "$APPVEYOR" != "" ]; then
  RANDOM_DOT_ORG_KEY=$(powershell 'Write-Host ($env:random_dot_org_key) -NoNewLine')
fi
cd betterrandom
if [ "$TRAVIS_JDK_VERSION" = "oraclejdk9" ]; then
  mv pom9.xml pom.xml
fi
# Remove git from path (causes conflicts), based on https://stackoverflow.com/a/370192
NO_GIT_PATH=`echo "${PATH}" | awk -v RS=: -v ORS=: '/git/ {next} {print}'`
PATH="${NO_GIT_PATH}" mvn -DskipTests -Darguments=-DskipTests -Dmaven.test.skip=true ${MAYBE_ANDROID_FLAG} clean package install &&\
cd ../benchmark &&\
PATH="${NO_GIT_PATH}" mvn -DskipTests ${MAYBE_ANDROID_FLAG} package &&\
cd target &&\
if [ "$TRAVIS" = "true" ]; then
    java -jar benchmarks.jar -f 1 -t 1 -foe true &&\
    java -jar benchmarks.jar -f 1 -t 2 -foe true
else
    java -jar benchmarks.jar -f 1 -t 1 -foe true -v EXTRA 2>&1 |\
        tee benchmark_results_one_thread.txt &&\
    java -jar benchmarks.jar -f 1 -t 2 -foe true -v EXTRA 2>&1 |\
        tee benchmark_results_two_threads.txt
fi && cd ../..
