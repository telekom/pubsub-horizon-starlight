<!--
Copyright 2024 Deutsche Telekom IT GmbH

SPDX-License-Identifier: Apache-2.0
-->

<p align="center">
  <img src="docs/img/starlight-icon.svg" alt="Starlight logo" width="200">
  <h1 align="center">Starlight</h1>
</p>

<p align="center">
  The event provider facing service designed for publishing events to Horizon.
</p>

<p align="center">
  <a href="#prerequisites">Prerequisites</a> •
  <a href="#configuration">Configuration</a> •
  <a href="#running-starlight">Running Starlight</a>
</p>

## Overview
Horizon's Starlight provides a REST endpoint allowing event providers to publish events. Its responsibilities include event acceptance, authentication/authorization, schema validation (optional), and publishing to Horizon's underlying message broker (Kafka).

We welcome and appreciate contributions from the developer community. Check our [contributing guide](LINK_TO_CONTRIBUTING_GUIDE.md) to get started!

## Prerequisites
For the optimal setup, ensure you have:

- A running instance of Kafka
- Access to a Kubernetes cluster on which the `Subscription` (subscriber.horizon.telekom.de) custom resource definition has been registered
- A running instance of ENI API, which has access to the resources of the Telekom Integration Platform control plane

## Configuration
Starlight configuration is managed through environment variables. Check the [complete list](docs/environment-variables.md) of supported environment variables for setup instructions.

## Running Starlight
### Locally
Before you can run Starlight locally you must have a running instance of Kafka and ENI API locally or forwarded from a remote cluster.
Additionally, you need to have a Kubernetes config at `${user.home}/.kube/config.main` that points to the cluster you want to use.

After that you can run Starlight in a dev mode using this command:
```shell
./gradlew bootRun --args='--spring.profiles.active=dev,publisher-mock'
```

## Code of Conduct

This project has adopted the [Contributor Covenant](https://www.contributor-covenant.org/) in version 2.1 as our code of conduct. Please see the details in our [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). All contributors must abide by the code of conduct.

By participating in this project, you agree to abide by its [Code of Conduct](./CODE_OF_CONDUCT.md) at all times.

## Licensing

This project follows the [REUSE standard for software licensing](https://reuse.software/).
Each file contains copyright and license information, and license texts can be found in the [./LICENSES](./LICENSES) folder. For more information visit https://reuse.software/.

### REUSE

For a comprehensive guide on how to use REUSE for licensing in this repository, visit https://telekom.github.io/reuse-template/.   
A brief summary follows below:

The [reuse tool](https://github.com/fsfe/reuse-tool) can be used to verify and establish compliance when new files are added.

For more information on the reuse tool visit https://github.com/fsfe/reuse-tool.