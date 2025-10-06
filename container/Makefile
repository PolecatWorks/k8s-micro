IMAGE_NAME=k8s-micro
VERSION?=2.0.0
BRANCHNAME?=
SHA?=
CHANGELIST?=-SNAPSHOT

export DOCKER_BUILDKIT=1

KAFKA_BOOTSTRAP := localhost:9092

export CONFLUENT_HOME := $(HOME)/Development/kafka/confluent-7.3.1
SCHEMA_REGISTRY_START:=$(CONFLUENT_HOME)/bin/schema-registry-start
ZOOKEEPER_SERVER_START:=$(CONFLUENT_HOME)/bin/zookeeper-server-start
KAFKA_SERVER_START:=$(CONFLUENT_HOME)/bin/kafka-server-start
KAFKA_TOPICS:=$(CONFLUENT_HOME)/bin/kafka-topics
KAFKA_PRODUCER:=$(CONFLUENT_HOME)/bin/kafka-console-producer
KAFKA_CONSUMER:=$(CONFLUENT_HOME)/bin/kafka-console-consumer

start-postgres:
	@brew services run postgresql@15
	# DB created wtih https://www.postgresql.org/docs/15/app-initdb.html
stop-postgres:
	@brew services stop postgresql@15

start-zookeeper:
	$(ZOOKEEPER_SERVER_START) $(CONFLUENT_HOME)/etc/kafka/zookeeper.properties

start-kafka:
	$(KAFKA_SERVER_START) $(CONFLUENT_HOME)/etc/kafka/server.properties

start-schema:
	$(SCHEMA_REGISTRY_START) $(CONFLUENT_HOME)/etc/schema-registry/schema-registry.properties


topics-list:
	$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --list

topics-create:
	$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --create --topic "test.topic"
	$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --create --topic "input"
	$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --create --topic "output"

topics-delete:
	$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --delete --topic "test.topic"
	$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --delete --topic "input"
	$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --delete --topic "output"

topic-input-write:
	echo a:bcdef | $(KAFKA_PRODUCER) --topic input --bootstrap-server $(KAFKA_BOOTSTRAP) --property parse.key=true --property key.separator=":"

topic-input-read:
	@$(KAFKA_CONSUMER) --bootstrap-server localhost:9092 --topic input --from-beginning --property print.key=true --property key.separator=":"



export JAVA_HOME = $(shell /usr/libexec/java_home -v 19.0.2)

mvnversion:
	@echo "Setting version to $(VERSION)$(BRANCHNAME)$(SHA)$(CHANGELIST)"
	@mvn versions:set -DnewVersion=$(VERSION)$(BRANCHNAME)$(SHA)$(CHANGELIST)

verify: mvnversion
	@mvn verify

package: mvnversion
	@mvn package ${MAVEN_ARGS}

run: MAVEN_ARGS=-DskipTests -Dversion=$(VERSION) -Dbranchname=$(BRANCHNAME) -Dsha=$(SHA) -Dchangelist=$(CHANGELIST)
run: package
	@java -jar  target/k8s-micro-$(VERSION)$(CHANGELIST)-jar-with-dependencies.jar

check-alive:
	@curl http://localhost:8079/hams/alive


docker-java-build:
	docker build --target java-build -t ${IMAGE_NAME}-java-build .
	docker image ls ${IMAGE_NAME}-java-build

docker-jre-build:
	docker build --target jre-build -t ${IMAGE_NAME}-jre-build .
	docker image ls ${IMAGE_NAME}-jre-build

docker-build:
	docker build --target publish -t ${IMAGE_NAME}:${VERSION} .
	docker image ls ${IMAGE_NAME}

docker-ma-build:
	# sudo apt-get install qemu binfmt-support qemu-user-static
	# docker run --rm --privileged multiarch/qemu-user-static --reset -p yes
	# docker buildx create --name monkey --use
	docker buildx build --platform linux/arm64,linux/amd64 --target publish -t ${IMAGE_NAME}:${VERSION} .
	docker buildx build --platform linux/arm64 --target publish --load -t ${IMAGE_NAME}:${VERSION}-arm64 .
	docker buildx build --platform linux/amd64 --target publish --load -t ${IMAGE_NAME}:${VERSION}-amd64 .
	# this also seems and issue https://github.com/docker/cli/issues/3350
	docker manifest create ${IMAGE_NAME}:${VERSION} --amend ${IMAGE_NAME}:${VERSION}-arm64 --amend ${IMAGE_NAME}:${VERSION}-amd64
	# Load FAILS because of this: https://github.com/docker/buildx/issues/59
	# docker buildx build --platform linux/amd64,linux/arm64 --target publish -t ${IMAGE_NAME}:${VERSION} .
	# docker buildx build --platform linux/arm64/v8,linux/amd64 --target publish -t ${IMAGE_NAME}:${VERSION} .
	# docker image ls ${IMAGE_NAME}

docker-java-build-bash: docker-java-build
	docker run -it ${IMAGE_NAME}-java-build /bin/bash

docker-jre-build-bash: docker-jre-build
	docker run -it ${IMAGE_NAME}-jre-build /bin/bash

docker-bash: docker-build
	docker run -it ${IMAGE_NAME}:${VERSION} /bin/bash

docker-run: docker-build
	docker run -it ${IMAGE_NAME}:${VERSION}

clean-branches:
	git branch --merged | egrep -v "(^\*|master|main|dev)" | xargs git branch -d


helm-upgrade:
	helm upgrade -i k8s-micro helm/k8s-micro

alpine-bash:
	kubectl run -i --tty alpine-$(subst .,-,${USER}) --image=alpine:latest --rm --restart=Never -- sh -c "until apk add curl bind-tools; do echo waiting for sidecar; sleep 3; done;sh"

kubectl-restart:
	kubectl rollout restart deployment k8s-micro

ingress-upgrade:
	helm upgrade --install ingress-nginx ingress-nginx --repo https://kubernetes.github.io/ingress-nginx --namespace ingress-nginx --create-namespace
	kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s
	kubectl apply -f dev/ingress.yaml

port-forward:
	echo curl command curl http://demo.localdev.me:8080/k8s-micro/v0/
	kubectl port-forward --namespace=ingress-nginx service/ingress-nginx-controller 8080:80 &

curl-check:
	curl http://demo.localdev.me:8080/k8s-micro/v0/

cleanbranches:
	git branch --merged| egrep -v "(^\*|master|main|dev)" | xargs git branch -d
