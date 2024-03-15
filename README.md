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
  <a href="#building-starlight">Building Starlight</a> •
  <a href="#configuration">Configuration</a> •
  <a href="#running-starlight">Running Starlight</a>
</p>

<!--
[![REUSE status](https://api.reuse.software/badge/github.com/telekom/pubsub-horizon-starlight)](https://api.reuse.software/info/github.com/telekom/pubsub-horizon-starlight)
-->
[![Gradle Build and Test](https://github.com/telekom/pubsub-horizon-starlight/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/telekom/pubsub-horizon-starlight/actions/workflows/gradle-build.yml)

## Overview
Horizon's Starlight provides a REST endpoint allowing event providers to publish events. Its responsibilities include event acceptance, authentication/authorization, schema validation (optional), and publishing to [Horizon's](https://github.com/telekom/pubsub-horizon) underlying message broker (Kafka).

## Prerequisites
For the optimal setup, ensure you have:

- A running instance of Kafka
- Access to a Kubernetes cluster on which the `Subscription` (subscriber.horizon.telekom.de) custom resource definition has been registered

## Building Starlight

### Gradle build

```bash
./gradlew build
```

The default docker base image is `azul/zulu-openjdk-alpine:21-jre`. This is customizable via the docker build arg `DOCKER_BASE_IMAGE`.
Please note that the default helm values configure the kafka compression type `snappy` which requires gcompat to be installed in the resulting image.
So either provide a base image with gcompat installed or change/disable the compression type in the helm values.

```bash
docker build -t horizon-starlight:latest --build-arg="DOCKER_BASE_IMAGE=<myjvmbaseimage:1.0.0>" . 
```

#### Multi-stage Docker build

To simplify things, we have also added a mult-stage Dockerfile to the respository, which also handles the Java build of the application in a build container. The resulting image already contains "gcompat", which is necessary for Kafka compression.

```bash
docker build -t horizon-starlight:latest . -f Dockerfile.multi-stage 
```

## Configuration
Starlight configuration is managed through environment variables. Check the [complete list](docs/environment-variables.md) of supported environment variables for setup instructions.

### Schema validation
Starlight basically supports schema validation for incoming events. Unfortunately this part of starlight is not yet ready for open-source.

If you can´t wait for this, you´re able to provide your own implementation. Basically this is done via a valid bean instance of the [SchemaStore](https://github.com/telekom/pubsub-horizon-spring-parent/blob/main/horizon-core/src/main/java/de/telekom/eni/pandora/horizon/schema/SchemaStore.java) interface from [horizon-pubsub-spring-parent](https://github.com/telekom/pubsub-horizon-spring-parent) module.
You are able to create your own spring-boot-starter with a custom implementation and use the environment variable `ADDITIONAL_SCHEMASTORE_IMPL` to build with your custom artifact and use the following configuration or make use of the available environment variables for these properties.

```yaml
starlight:
  features:
    schemaValidation: true

# only used for schema validation (not yet possible as OSS for starlight)
eniapi:
  baseurl: https://api.example.com/
  refreshInterval: 60000

# only used for schema validation (not yet possible as OSS for starlight)
oidc:
  issuerUrl: https://oauth.example.com/
  clientId: foo
  clientSecret: bar
```

## Running Starlight
### Locally
Before you can run Starlight locally you must have a running instance of Kafka  locally or forwarded from a remote cluster.
Additionally, you need to have a Kubernetes config at `${user.home}/.kube/config.main` that points to the cluster you want to use.

After that you can run Starlight in a dev mode using this command:
```shell
./gradlew bootRun --args='--spring.profiles.active=publisher-mock'
```

## Documentation

Read more about the software architecture and the general process flow of Horizon Starlight in [docs/architecture.md](docs/architecture.md).

## Contributing

We're committed to open source, so we welcome and encourage everyone to join its developer community and contribute, whether it's through code or feedback.  
By participating in this project, you agree to abide by its [Code of Conduct](./CODE_OF_CONDUCT.md) at all times.


## Code of Conduct

This project has adopted the [Contributor Covenant](https://www.contributor-covenant.org/) in version 2.1 as our code of conduct. Please see the details in our [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). All contributors must abide by the code of conduct.

## Licensing

This project follows the [REUSE standard for software licensing](https://reuse.software/).
Each file contains copyright and license information, and license texts can be found in the [./LICENSES](./LICENSES) folder. For more information visit https://reuse.software/.

### REUSE

For a comprehensive guide on how to use REUSE for licensing in this repository, visit https://telekom.github.io/reuse-template/.   
A brief summary follows below:

The [reuse tool](https://github.com/fsfe/reuse-tool) can be used to verify and establish compliance when new files are added.

For more information on the reuse tool visit https://github.com/fsfe/reuse-tool.