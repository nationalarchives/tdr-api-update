# TDR API update task

This project is part of the the [Transfer Digital Records] project.

It is a Lambda that runs as part of the backend file checks. When a file
check (antivirus, file format, etc.) finishes, it generates a message containing
the results of the check, and puts it on a queue that this task is subscribed
to. This task takes the output and saves it to the API.

See the [TDR architecture diagram] for how the API update Lambda fits into the
rest of the workflow.

[Transfer Digital Records]: https://github.com/nationalarchives/tdr-dev-documentation/
[TDR architecture diagram]: https://github.com/nationalarchives/tdr-dev-documentation/blob/master/beta-architecture/beta-architecture.md
