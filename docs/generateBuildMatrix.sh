#!/bin/bash

# Generates markdown table of build job status

allJdkVersions=("jdk8u" "jdk11u" "jdk17u" "jdk21" "jdk")
buildFile="/tmp/build.txt"
buildJobFile="/tmp/build_jobs.txt"
buildLinksFile="/tmp/build_links.txt"
excludedKeywords=("SmokeTests" "hotspot" "corretto" "bisheng" "dragonwell" "openj9")

if [[ -f ${buildFile} ]]; then
  echo "Removing previous ${buildFile} ${buildJobFile} ${buildLinksFile} files"
  rm ${buildFile}
  rm ${buildJobFile}
  rm ${buildLinksFile}
fi

for i in ${allJdkVersions[@]}; do
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
for jdkVersionX in ${allJdkVersions[@]}; do
  echo -n " ${jdkVersionX} |" | sed -e 's/jdk/Java /;s/u//;s/  / HEAD /' >> ${buildFile}
done
# Delimiter row
echo -n $'\n|------' >> ${buildFile} # to match Platform column
for i in ${allJdkVersions[@]}; do
  echo -n "|----" >> ${buildFile}
done
echo "|" >> ${buildFile}

# Prep buildLinksFile
echo "" >> ${buildLinksFile}

rowNum=1

cat ${buildJobFile}| cut -d'/' -f2 | sed -r 's/jdk[0-9]*u?\-//g' | sort | uniq | while read buildName;
do
    # buildName should be of the form: aix-ppc64-temurin
    echo -n "| ${buildName} | " >> ${buildFile}
    colNum=1
    for jdkVersionX in ${allJdkVersions[@]}; do
        jenkinsJobName="${jdkVersionX}-${buildName}"
        code=$(curl -L -s -o /dev/null -w "%{http_code}" "https://ci.adoptium.net/job/build-scripts/job/jobs/job/${jdkVersionX}/job/${jenkinsJobName}")
        if [[ ${code} = 200 ]]; then
            echo -n "[![Build Status][i-r${rowNum}c${colNum}]](j-r${rowNum}c${colNum})" >> ${buildFile}
            echo "[i-r${rowNum}c${colNum}]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/${jdkVersionX}/${jenkinsJobName}" >> ${buildLinksFile}
            echo "[j-r${rowNum}c${colNum}]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/${jdkVersionX}/job/${jenkinsJobName}" >> ${buildLinksFile}
        else
            echo -n "N/A" >> ${buildFile}
        fi
        echo -n " | " >> ${buildFile}
        ((++colNum))
    done
    echo "" >> ${buildFile}
    ((++rowNum))
done

cat ${buildLinksFile} >> ${buildFile}

rm ${buildJobFile}
rm ${buildLinksFile}

echo "Complete - markdown out has been generated in ${buildFile}"
