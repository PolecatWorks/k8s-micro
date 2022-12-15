# https://codefresh.io/blog/java_docker_pipeline/

FROM eclipse-temurin:11 as java-build
# ----
# Install Maven
# RUN apk add --no-cache curl tar bash
ARG MAVEN_VERSION=3.8.6
ARG USER_HOME_DIR="/root"
RUN mkdir -p /usr/share/maven && \
curl -fsSL http://apache.osuosl.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar -xzC /usr/share/maven --strip-components=1 && \
ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"
# speed up Maven JVM a bit
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
# ENTRYPOINT ["/usr/bin/mvn"]
# ----
# Install project dependencies and keep sources
# make source folder
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
# install maven dependency packages (keep in image)
COPY pom.xml ./
RUN mvn -T 1C install && rm -rf target
# copy other source files (keep in image)
COPY src ./src/
RUN mvn verify


FROM eclipse-temurin:11 as jre-build
# https://hub.docker.com/_/eclipse-temurin

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

# # Define your base image
# # FROM debian:buster-slim
FROM ubuntu:lunar as publish
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

# # Continue with your application deployment
RUN mkdir /opt/app
COPY  --from=java-build /usr/src/app/target/k8s-micro-1.0-SNAPSHOT-jar-with-dependencies.jar /opt/app/japp.jar
CMD ["java", "-jar", "/opt/app/japp.jar"]
