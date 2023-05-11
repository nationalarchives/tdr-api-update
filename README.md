# TDR API update task

This project is part of the the [Transfer Digital Records] project.

It is a Lambda that runs as part of the backend file checks. These tasks are run in a loop inside a step function.
Once all of the antivirus, file format and checksum checks are complete, they are sent to this lambda which updates the API with the results.
The lambda also updates file status and consignment status results.

See the [TDR architecture diagram] for how the API update Lambda fits into the rest of the workflow.

[Transfer Digital Records]: https://github.com/nationalarchives/tdr-dev-documentation/
[TDR architecture diagram]: https://github.com/nationalarchives/tdr-dev-documentation/blob/master/beta-architecture/beta-architecture.md
