#!/bin/bash

# Generates markdown table of build job status

allPlatforms=("jdk8u" "jdk11u" "jdk17u" "jdk18u" "jdk")
buidFile="/tmp/build.txt"
buildJobFile="/tmp/build_jobs.txt"

if [[ -f ${buidFile} ]]; then
  echo "Removing previous ${buidFile} ${buildJobFile} files"
  rm ${buidFile}
  rm ${buildJobFile}
fi

for i in ${allPlatforms[@]};
do
    curl -s "https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/${i}/" | egrep -o "job/${i}-[^\/]+" >> ${buildJobFile}
done

# The sed command fails on Mac OS X, but those users can install gnu-sed
echo "This will take a few minutes to complete."
# Header row
echo -n "| Platform |" > ${buidFile}
for p in ${allPlatforms[@]};do
  echo -n " ${p} |" | sed -e 's/jdk/Java /;s/u//;s/  / HEAD/' >> ${buidFile}
done
# Delimer row
echo -n $'\n|------' >> ${buidFile} # to match Platform column
for i in ${allPlatforms[@]} ;do
  echo -n "|----" >> ${buidFile}
done
echo "|" >> ${buidFile}

cat ${buildJobFile}| cut -d'/' -f2 | sed -r 's/jdk[0-9]*u?\-//g' | sort | uniq | while read buildName;
do
    # buildName should be of the form: aix-ppc64-hotspot
    echo -n "| ${buildName} | " >> ${buidFile}
    for i in ${allPlatforms[@]};
    do
        code=$(curl -L -s -o /dev/null -w "%{http_code}" "https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/${i}/job/${i}-${buildName}")
        if [[ ${code} = 200 ]]; then
            echo -n "[![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/${i}/${i}-${buildName})](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/${i}/job/${i}-${buildName})" >> "/tmp/build.txt"
        else
            echo -n "N/A" >> ${buidFile}
        fi

        echo -n " | " >> ${buidFile}
    done
    echo "" >> ${buidFile}
done

echo "Complete - markdown out has been generated in ${buidFile}"
