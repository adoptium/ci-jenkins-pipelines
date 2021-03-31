#!/bin/bash

# Generates markdown table of build job status

if [[ -f "/tmp/build.txt" ]]; then
  echo "Removing previous /tmp/build.txt /tmp/build_jobs.txt files"
  rm "/tmp/build.txt"
  rm "/tmp/build_jobs.txt"
fi

for i in "jdk8u" "jdk11u" "jdk15u" "jdk16u" "jdk";
do
    curl -s "https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/${i}/" | egrep -o "job/${i}-[^\/]+" >> "/tmp/build_jobs.txt"
done

# The sed command fails on Mac OS X, but those users can install gnu-sed
echo "This will take a few minutes to complete."

echo "| Platform                  | Java 8 | Java 11| Java 15 | Java 16 | Java HEAD |" > "/tmp/build.txt"
echo "| ------------------------- | ------ | ------ | ------- | ------- | --------- |" >> "/tmp/build.txt"

cat "/tmp/build_jobs.txt" | cut -d'/' -f2 | sed -r 's/jdk[0-9]*u?\-//g' | sort | uniq | while read buildName;
do
    # buildName should be of the form: aix-ppc64-hotspot
    echo -n "| ${buildName} | " >> "/tmp/build.txt"
    for i in "jdk8u" "jdk11u" "jdk15u" "jdk16u" "jdk";
    do
        code=$(curl -L -s -o /dev/null -w "%{http_code}" "https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/${i}/job/${i}-${buildName}")
        if [[ ${code} = 200 ]]; then
            echo -n "[![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/${i}/${i}-${buildName})](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/${i}/job/${i}-${buildName})" >> "/tmp/build.txt"
        else
            echo -n "N/A" >> "/tmp/build.txt"
        fi

        echo -n " | " >> "/tmp/build.txt"
    done
    echo "" >> "/tmp/build.txt"
done

echo "Complete - markdown out has been generated in /tmp/build.txt"
