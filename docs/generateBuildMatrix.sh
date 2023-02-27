#!/bin/bash

# Generates markdown table of build job status

allPlatforms=("jdk8u" "jdk11u" "jdk17u" "jdk20" "jdk")
buildFile="/tmp/build.txt"
buildJobFile="/tmp/build_jobs.txt"
excludedKeywords=("SmokeTests" "hotspot" "corretto" "bisheng" "dragonwell" "openj9")

if [[ -f ${buildFile} ]]; then
  echo "Removing previous ${buildFile} ${buildJobFile} files"
  rm ${buildFile}
  rm ${buildJobFile}
fi

for i in ${allPlatforms[@]}; do
    curl -s "https://ci.adoptium.net/job/build-scripts/job/jobs/job/${i}/" | egrep -o "job/${i}-[^\/]+" >> ${buildJobFile}
done

# Filter out jobs matching excludedKeywords
for key in ${excludedKeywords[@]}; do
  sed -i "/${key}/d" ${buildJobFile}
done

# The sed command fails on Mac OS X, but those users can install gnu-sed
echo "This will take a few minutes to complete."
# Header row
echo -n "| Platform |" > ${buildFile}
for p in ${allPlatforms[@]}; do
  echo -n " ${p} |" | sed -e 's/jdk/Java /;s/u//;s/  / HEAD/' >> ${buildFile}
done
# Delimiter row
echo -n $'\n|------' >> ${buildFile} # to match Platform column
for i in ${allPlatforms[@]}; do
  echo -n "|----" >> ${buildFile}
done
echo "|" >> ${buildFile}

cat ${buildJobFile}| cut -d'/' -f2 | sed -r 's/jdk[0-9]*u?\-//g' | sort | uniq | while read buildName;
do
    # buildName should be of the form: aix-ppc64-temurin
    echo -n "| ${buildName} | " >> ${buildFile}
    for i in ${allPlatforms[@]}; do
        code=$(curl -L -s -o /dev/null -w "%{http_code}" "https://ci.adoptium.net/job/build-scripts/job/jobs/job/${i}/job/${i}-${buildName}")
        if [[ ${code} = 200 ]]; then
            echo -n "[![Build Status](https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/${i}/${i}-${buildName})](https://ci.adoptium.net/job/build-scripts/job/jobs/job/${i}/job/${i}-${buildName})" >> "/tmp/build.txt"
        else
            echo -n "N/A" >> ${buildFile}
        fi

        echo -n " | " >> ${buildFile}
    done
    echo "" >> ${buildFile}
done

echo "Complete - markdown out has been generated in ${buildFile}"
