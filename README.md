# TDR API update task

This project is part of the the [Transfer Digital Records] project.

It is a Lambda that runs as part of the backend file checks. When a file
check (antivirus, file format, etc.) finishes, it generates a message containing
the results of the check, and puts it on a queue that this task is subscribed
to. This task takes the output and saves it to the API.

See the [TDR architecture diagram] for how the API update Lambda fits into the
rest of the workflow.

## Adding new environment variables to the tests
The environment variables in the deployed lambda are encrypted using KMS and then base64 encoded. These are then decoded in the lambda. Because of this, any variables in `src/test/resources/application.conf` which come from environment variables in `src/main/resources/application.conf` need to be stored base64 encoded. There are comments next to each variable to say what the base64 string decodes to. If you want to add a new variable you can run `echo -n "value of variable" | base64 -w 0` and paste the output into the test application.conf

[Transfer Digital Records]: https://github.com/nationalarchives/tdr-dev-documentation/
[TDR architecture diagram]: https://github.com/nationalarchives/tdr-dev-documentation/blob/master/beta-architecture/beta-architecture.md
