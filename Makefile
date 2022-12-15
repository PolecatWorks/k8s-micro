IMAGE_NAME=k8s-micro



docker-java-build:
	docker build --target java-build -t ${IMAGE_NAME}-java-build .
	docker image ls ${IMAGE_NAME}-java-build

docker-jre-build:
	docker build --target jre-build -t ${IMAGE_NAME}-jre-build .
	docker image ls ${IMAGE_NAME}-jre-build

docker-publish-build:
	docker build --target publish -t ${IMAGE_NAME}-publish .
	docker image ls ${IMAGE_NAME}-publish

docker-java-build-bash: docker-java-build
	docker run -it ${IMAGE_NAME}-java-build /bin/bash

docker-jre-build-bash: docker-jre-build
	docker run -it ${IMAGE_NAME}-jre-build /bin/bash

docker-publish-bash: docker-publish-build
	docker run -it ${IMAGE_NAME}-publish /bin/bash
