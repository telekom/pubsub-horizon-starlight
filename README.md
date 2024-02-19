<p align="center">
  <img src="docs/img/starlight-icon.svg" alt="Starlight logo" width="200">
  <h1 align="center">Starlight</h1>
</p>

<p align="center">
  The event provider facing service designed for publishing events to Horizon.
</p>

<p align="center">
  <img src="https://shields.devops.telekom.de/badge/Made%20with%20%E2%9D%A4%20%20by-%F0%9F%90%BC-blue" alt="Made With Love Badge"/>
  <img src="https://gitlab.devops.telekom.de/dhei/teams/pandora/products/horizon/starlight/badges/develop/coverage.svg" alt="Coverage Badge"/>
  <img src="https://gitlab.devops.telekom.de/dhei/teams/pandora/products/horizon/starlight/-/badges/release.svg" alt="Release Badge"/>
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

