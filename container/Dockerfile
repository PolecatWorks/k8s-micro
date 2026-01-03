# https://codefresh.io/blog/java_docker_pipeline/
ARG VERSION=2.0.0
ARG CHANGELIST=-SNAPSHOT

ARG MAVEN_VERSION=3.9.6-eclipse-temurin-24

FROM maven:${MAVEN_VERSION} AS java-build


# speed up Maven JVM a bit
# ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
# ENTRYPOINT ["/usr/bin/mvn"]
# ----
# Install project dependencies and keep sources
# make source folder
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -T 1C install && rm -rf target
# copy other source files (keep in image)
COPY src ./src/
ARG VERSION
ARG CHANGELIST
RUN --mount=type=cache,target=/root/.m2 mvn verify -Dversion=${VERSION} -Dchangelist=${CHANGELIST}
RUN jar --file=target/k8s-micro-${VERSION}${CHANGELIST}-jar-with-dependencies.jar --describe-module > modules.txt


FROM maven:${MAVEN_VERSION} AS jre-build
# https://hub.docker.com/_/eclipse-temurin

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base,java.naming,java.desktop,java.sql,java.management \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime



# Define your base image
FROM ubuntu:24.04 AS publish
ARG VERSION
ARG CHANGELIST

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

# # Continue with your application deployment
RUN mkdir /opt/app
COPY  --from=java-build /target/k8s-micro-${VERSION}${CHANGELIST}-jar-with-dependencies.jar /opt/app/k8s-micro.jar
COPY --from=java-build /modules.txt /opt/app/
EXPOSE 8080/tcp
ENTRYPOINT ["java", "-jar", "/opt/app/k8s-micro.jar"]
