# PR Testers

openjdk-build is an open source project, therefore, we need to ensure that the code that is being deployed to our master branch doesn't break any existing code and actually works as expected.
To achieve this level of testing, we use various jobs to compile, lint and test the code as well as running demo pipelines in a controlled sandbox environment if need be.
The demo pipelines are colloquially known as "The PR Tester" and the others are generally just referred to as "`checkname` check".

## When they're used

Except for the [#openjdk-build-pr-tester](#openjdk-build-pr-tester), all of the [test groups](#what-they-are) are executed automatically on every PR and are defined inside the [.github/workflows directory](.github/workflows).
These tests lint & compile the code you have altered, as well as executing full JDK builds using your code.
Every new pull request to this repository that alters any groovy code OR that will likely affect our Jenkins builds should have the PR tester ([#openjdk-build-pr-tester](#openjdk-build-pr-tester)) run on it at least once to verify the changes don't break anything significant inside a Jenkins build environment (documentation changes being excluded from this rule).

## What they are

There are four "groups" of tests that can be run on each PR:

- [#Test](#Test)
- [#openjdk-build-pr-tester](#openjdk-build-pr-tester) (**OPTIONAL, SEE [#When they're used](#When-they're-used)**)

The results of these jobs will appear as [GitHub Status Check Results](https://docs.github.com/en/github/administering-a-repository/about-required-status-checks) at the bottom of the PR being tested:
![Image of PR Tester Checks](./images/pr_tester_checks.png)

### Test

This group consists of [GitHub Status Checks](https://docs.github.com/en/free-pro-team@latest/github/collaborating-with-issues-and-pull-requests/about-status-checks) run inside GitHub itself. They unit test any code changes you have made.

#### Groovy

- The job runs our [groovy testing suite](https://github.com/adoptium/ci-jenkins-pipelines/tree/master/pipelines/src/test/groovy). The various tests in this directory ensure that our jenkins library classes return the correct information.

- To run the suite locally:

```bash
cd pipelines/
./gradlew --info test
```

### openjdk-build-pr-tester

- **Seen in the PR Status Checks as `pipeline-build-check`, the job is located [here](https://ci.adoptopenjdk.net/job/build-scripts-pr-tester/job/openjdk-build-pr-tester/)**
- This job runs the a set of [sandbox pipelines](https://ci.adoptopenjdk.net/job/build-scripts-pr-tester/job/build-test/) to test the changes that you have made to our codebase.
- It first executes [kick_off_tester.groovy](pipelines/build/prTester/kick_off_tester.groovy) which in turn kicks off our [pr_test_pipeline](pipelines/build/prTester/pr_test_pipeline.groovy), the main base file for this job.
- NOTE: This tester is only really worth running if your code changes affect our groovy code OR Jenkins environment. Otherwise, the [#Build](#Build) jobs are sufficient enough to flag any problems with your code.

#### Usage

The tester has it's own admin and white lists on Jenkins.
If you are on either list, the PR tester will run against your PR whenever you comment `run tests` and will also allow you access to various commands you can run on your own PR or on someone else's:

##### `run tests`

- Executes a new [#openjdk-build-pr-tester](#openjdk-build-pr-tester) job against this PR. These jobs will populate the GitHub status checks field as described above.

Please be patient as the tester does not run concurrently so it may take some time to execute the jobs if there is a long job queue. You can track the progress of it in [Jenkins](https://ci.adoptopenjdk.net/job/build-scripts-pr-tester/) OR look at the status check message:

- Example of a PR that is in the queue:
  ![Image of queued tester](./images/pr_tester_queued.png)
- One that is at the front of the queue and currently being tested:
  ![Image of building tester](./images/pr_tester_building.png)

- When the tester begins, it will generate several downstream jobs; each of which will have their own job status:
  ![Image of downstream Job](./images/pr_tester_downstream.png)

- When the tester is done, it will return a response comment to the PR with feedback on the testing similar to the following:
![Image of test result](./images/pr_tester_result.png)

- The message will vary depending on the result of the test. Please remember however, that failed tests may be due to existing problems in the nightly builds, not your code.

If you're unsure if the tests failed because of your changes or not, check our [issue board](https://github.com/adoptium/ci-jenkins-pipelines/issues) and our [triage doc](https://docs.google.com/document/d/1vcZgHJeR8rW8U8OD23Uob7A1dbLrtkURZUkinUp7f_w/edit?usp=sharing) for the existing error. If your job was aborted, check the log to see who aborted it.

- 🟢**SUCCESS** 🟢 All the downstream jobs passed, congratulations!
- 🟠**FAILURE** 🟠 Some of the downstream jobs failed OR the job was aborted. Check the link in the field at the bottom of the PR for the job link to see exactly where it went wrong.
- 🔴**ERROR** 🔴 Something more serious went wrong with the tester itself. Please raise an issue with a link to the job, the error encountered and your PR that caused it (again, you can use the link at the bottom to see exactly what happened).

##### `run tests quick`

Similar to `run tests` but runs a subset of jdk test jobs. Without specific jdk version, it uses 17, otherwise loops over given versions
Example:
`run tests quick` is the same as `run tests quick 19`
`run tests quick 11, 19, 8` generates all jobs then runs openjdk pipeline on 8, 11 and 19

##### `add to whitelist`

- **ADMIN COMMAND ONLY**
- This command adds a new user to the whitelist but not to the admin list of the [#openjdk-build-pr-tester](#openjdk-build-pr-tester) job. As of typing this, there is [currently no way to check if you have the correct permissions](https://github.com/adoptium/temurin-build/issues/2055#issuecomment-688801090).
- Should you want to be promoted to the whitelist, please contact one of the admins through [#infrastructure](https://adoptopenjdk.slack.com/archives/C53GHCXL4) in Slack.
- Should you want the up to date admin or white list, check the configuration of the [openjdk-build-pr-tester](https://ci.adoptopenjdk.net/job/build-scripts-pr-tester/job/openjdk-build-pr-tester/) job. If you don't have the permissions to view the configuration, then try out the `add to whitelist` and `run tests` commands on a test PR to see if they work.
