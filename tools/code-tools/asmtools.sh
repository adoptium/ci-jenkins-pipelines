
#!/bin/bash
# shellcheck disable=SC2035,SC2155
set -euo pipefail
WORKSPACE=$PWD

function generateArtifact() {
    local  branchOrTag=${1}
    export JAVA_HOME=${2}
    local testLog=test.log
    git checkout $branchOrTag
    echo "Moving into maven build..."
    pushd maven
      echo "Removing asmtools $branchOrTag old maven wrapper"
      rm -rf src target pom.xml $testLog
      #export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF8 -Xdoclint:none -Dmaven.javadoc.skip=true -Dgpg.skip"
      echo "Generating asmtools $branchOrTag maven wrapper"
      sh mvngen.sh
      echo "Cleaning possible previous run"
      mvn clean
      echo "Running asmtools $branchOrTag tests by $JAVA_HOME"
      mvn test 2>&1 | tee $testLog || echo "Test now correctly fails, this have to be fixed upstream"
      echo "Running asmtools $branchOrTag build by $JAVA_HOME"
      mvn package -DskipTests # mvn install will do much more, but I doubt we wish that (javadoc, sources, gpg sign...)
      echo "Moving down to target"
      pushd target
        echo "Asmtools $branchOrTag artifact:"
        ls -l
        local mainArtifact=`ls asmtools*.jar`
        echo "Copying maven/target/$mainArtifact file to RESULTS_DIR($RESULTS_DIR)"
        cp  $mainArtifact $RESULTS_DIR
        if [ -e surefire-reports ] ; then
          local testResults=`echo $mainArtifact | sed "s/.jar/-tests.tar.gz/"`
          echo "Compressing and archiving test results as $testResults"
          tar -czf $testResults surefire-reports
          echo "Copying maven/target/$testResults file to RESULTS_DIR($RESULTS_DIR)"
          cp  $testResults $RESULTS_DIR
          pushd ..
            echo "Moving maven/$testLog file to RESULTS_DIR($RESULTS_DIR)"
            mv $testLog $RESULTS_DIR/$mainArtifact.tests.txt
          popd
        else
          echo "No test results!"
        fi
      popd
    popd
}

REPO_DIR="asmtools"
if [ ! -e $REPO_DIR ] ; then
  git clone https://github.com/openjdk/$REPO_DIR.git
fi
jvm_dir="/usr/lib/jvm/"
find ${jvm_dir} -maxdepth 1 | sort
echo "Available jdks 8 in ${jvm_dir}:"
find ${jvm_dir} -maxdepth 1 | sort | grep -e java-1.8.0-  -e jdk-8
echo "Available jdks 17 in ${jvm_dir}:"
find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17-     -e jdk-17
jdk08=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-1.8.0-  -e jdk-8   | head -n 1))
jdk17=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17-     -e jdk-17  | head -n 1))
pushd $REPO_DIR
  RESULTS_DIR="$(pwd)"
  latestRelease=`git tag -l | tail -n 2 | head -n 1`
  generateArtifact "master" "$jdk08"
  generateArtifact "at8" "$jdk17"
  # 7.0-b09 had not yet have maven integration, enable with b10 out
  # generateArtifact "$latestRelease" "$jdk08"
  echo "Creating checksums all asmtools*.jar"
  for file in `ls asmtools*.jar asmtools*-tests.tar.gz` ; do
      sha256sum $file > $file.sha256sum.txt
  done
  git checkout master
popd
