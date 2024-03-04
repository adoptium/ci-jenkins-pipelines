/*
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import common.VersionInfo
import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor
import groovy.json.JsonOutput

import java.util.regex.Matcher

class ParseVersion {

    static void main(String[] args) {
        OptionAccessor parsedArgs = parseArgs(args)
        VersionInfo version
        if (parsedArgs.s) {
            version = parseFromStdIn(args)
        } else {
            def versionString = args[args.length - 2]
            def adoptBuildNumber = args[args.length - 1]
            version = new VersionInfo(this).parse(versionString, adoptBuildNumber)
        }

        printVersion(parsedArgs, version)
    }

    private static void printVersion(OptionAccessor parsedArgs, VersionInfo version) {
        if (parsedArgs.f) {
            def toPrint = ((String) parsedArgs.getProperty("f")).split(",")
            toPrint.each { arg ->
                if (arg == "openjdk-semver") {
                    println(version.formOpenjdkSemver())
                } else {
                    println(version.getProperty(arg))
                }
            }
        } else {
            println(JsonOutput.prettyPrint(JsonOutput.toJson(version)))
        }
    }

    private static VersionInfo parseFromStdIn(String[] args) {
        def reader = System.in.newReader()
        def line
        while ((line = reader.readLine()) != null) {
            Matcher matcher = (line =~ /.*\(build (?<version>.*)\).*/)
            if (matcher.matches()) {
                VersionInfo version = new VersionInfo(this).parse(matcher.group("version"), args[args.length - 1])
                return version
            }
        }
        throw new RuntimeException("No java versions found. Expected to read input from java -version")
    }

    private static OptionAccessor parseArgs(String[] args) {
        CliBuilder cliBuilder = new CliBuilder()
        cliBuilder.s('read input from stdin', args: 0)
        cliBuilder.f('print given field from data', args: 1)

        return cliBuilder.parse(args)
    }
}