# k8s-micro

Create a tiny kotlin service to create IoT messages via kafka and process them calculcate on the fly data.
This service needs to be small to allow it to be run on edge nodes which are typically
small with minimal memory and CPU footprint.

Target System footprint for edge nodes: 200M image and 200M memory.

https://github.com/papsign/Ktor-OpenAPI-Generator

This is a small container and helm chart to provide a basic microservice with trimmings.

The objective is to provide:
* [x] Fast incremental build (IDE and docker)
* [x] CLI arguments
* [x] Config loading
* [x] Health services (Alive and Ready)
* [x] Startup hook
* [x] Shutdown hook
* [x] Logging
* [x] Monitoring (https://ktor.io/docs/micrometer-metrics.html#prometheus_endpoint)
* [x] Extensible Monitoring (customer monitoring objects)
* [x] Basic Monitoring (http system, JVM)
* [x] OpenAPI (https://ktor.io/docs/openapi.html)
* [x] git good practices (pre-commit, etc)
* [x] Modern JDK11
* [x] JDK25
* [x] Coroutines
* [ ] KTOR development mode (https://ktor.io/docs/auto-reload.html#watch-paths)
* [x] License
* [ ] Useful README
  * [ ] README describing dev process and reloads, etc
* [ ] Review items
  * [x] Use of threads + coroutines
  * [x] Structure of code/modules - Health separation from functional code
* [ ] Multiarch Docker images
* [ ] Capture pod id into service configs
* [ ] Print routes on startup: https://github.com/ktorio/ktor/issues/1252#issuecomment-551304202
* [ ] Avro and Kafka : https://stefano-zanella.medium.com/publishing-avro-records-to-kafka-with-kotlin-avro4k-and-spring-boot-ba6be23bcba2
* [ ] Useful reference for kafka and Avro4k : https://github.com/thake/avro4k-kafka-serializer/blob/main/src/main/kotlin/com/github/thake/kafka/avro4k/serializer/AbstractKafkaAvro4kSerDe.kt
* [ ] PreProcessor for Hoplite to read K8s Secrets from files
*

# Approach

## Multiarch images

Create multiarch images amd64 and arm64 with Docker
Prepare by installing qemu. Then add support to docker buildx to use qemu.
Once the images are build they need to be loaded from buildx to docker itself. Load is currently broken for multiarch so needs to be done per image https://github.com/docker/buildx/issues/59
Once the images are published as platform specific images we can then publish a manifest with the multiarch image.

	  sudo apt-get install qemu binfmt-support qemu-user-static
    docker run --rm --privileged multiarch/qemu-user-static --reset -p yes
    docker buildx create --name mybuildercontext --use

This link https://github.com/docker/cli/issues/3350 possibly indicates that it may be possible to create the multiarch images locally and push as a single image.
BUT on confirmation this does not work as it is required for the images to be pushed to a repository.

    docker push {IMAGE_NAME}:${VERSION}-amd64
    docker push {IMAGE_NAME}:${VERSION}-arm64
    docker manifest create ${IMAGE_NAME}:${VERSION} ${IMAGE_NAME}:${VERSION}-arm64 ${IMAGE_NAME}:${VERSION}-amd64


# Dependencies

This repo uses the following dependencies:
[![pre-commit](https://img.shields.io/badge/pre--commit-enabled-brightgreen?logo=pre-commit)](https://github.com/pre-commit/pre-commit)
[![Kotlin Badge](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=fff&style=flat-square)](https://kotlinlang.org)
[![Prometheus Badge](https://img.shields.io/badge/Prometheus-E6522C?logo=prometheus&logoColor=fff&style=flat-square)](https://prometheus.io)
[![Docker Badge](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=fff&style=flat-square)](https://www.docker.com)
[![OpenJDK Badge](https://img.shields.io/badge/OpenJDK-FFF?logo=openjdk&logoColor=000&style=flat-square)](https://openjdk.org)
[![Apache Maven Badge](https://img.shields.io/badge/Apache%20Maven-C71A36?logo=apachemaven&logoColor=fff&style=flat-square)](https://maven.apache.org)
[![GitHub Badge](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=fff&style=flat-square)](https://github.com)

* JDK25: eclipse-temurin:25
* KTOR
* Hoplite
* Clikt

# Create k8s secrets

kubectl create secret generic k8s-micro --from-literal=username=user0 --from-literal=password=pass0

## GitHub Status

![GitHub](https://img.shields.io/github/license/polecatworks/k8s-micro?style=flat-square)
![GitHub last commit](https://img.shields.io/github/last-commit/polecatworks/k8s-micro?style=flat-square)
![GitHub contributors](https://img.shields.io/github/contributors/polecatworks/k8s-micro?style=flat-square)
