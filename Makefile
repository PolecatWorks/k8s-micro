IMAGE_NAME=k8s-micro
VERSION=2.0.0

export JAVA_HOME = $(shell /usr/libexec/java_home -v 19.0.1)

verify:
	@mvn verify

package:
	@mvn package ${MAVEN_ARGS}

run: MAVEN_ARGS=-DskipTests
run: package
	@java -jar  target/k8s-micro-1.0-SNAPSHOT-jar-with-dependencies.jar

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
