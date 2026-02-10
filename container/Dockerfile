# https://codefresh.io/blog/java_docker_pipeline/
ARG VERSION=2.0.0
ARG CHANGELIST=-SNAPSHOT

ARG MAVEN_VERSION=3-eclipse-temurin-25

FROM maven:${MAVEN_VERSION} AS java-build


# speed up Maven JVM a bit
# ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
# ENTRYPOINT ["/usr/bin/mvn"]
# ----
# Install project dependencies and keep sources
# make source folder
COPY pom.xml ./
# use the certs from the host user
RUN --mount=type=secret,id=my_certs,target=/usr/local/share/ca-certificates/ \
    update-ca-certificates && \
    keytool -list -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

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

# java.base@11.0.17
# java.compiler@11.0.17
# java.datatransfer@11.0.17
# java.desktop@11.0.17
# java.instrument@11.0.17
# java.logging@11.0.17
# java.management@11.0.17
# java.management.rmi@11.0.17
# java.naming@11.0.17
# java.net.http@11.0.17
# java.prefs@11.0.17
# java.rmi@11.0.17
# java.scripting@11.0.17
# java.se@11.0.17
# java.security.jgss@11.0.17
# java.security.sasl@11.0.17
# java.smartcardio@11.0.17
# java.sql@11.0.17
# java.sql.rowset@11.0.17
# java.transaction.xa@11.0.17
# java.xml@11.0.17
# java.xml.crypto@11.0.17
# jdk.accessibility@11.0.17
# jdk.aot@11.0.17
# jdk.attach@11.0.17
# jdk.charsets@11.0.17
# jdk.compiler@11.0.17
# jdk.crypto.cryptoki@11.0.17
# jdk.crypto.ec@11.0.17
# jdk.dynalink@11.0.17
# jdk.editpad@11.0.17
# jdk.hotspot.agent@11.0.17
# jdk.httpserver@11.0.17
# jdk.internal.ed@11.0.17
# jdk.internal.jvmstat@11.0.17
# jdk.internal.le@11.0.17
# jdk.internal.opt@11.0.17
# jdk.internal.vm.ci@11.0.17
# jdk.internal.vm.compiler@11.0.17
# jdk.internal.vm.compiler.management@11.0.17
# jdk.jartool@11.0.17
# jdk.javadoc@11.0.17
# jdk.jcmd@11.0.17
# jdk.jconsole@11.0.17
# jdk.jdeps@11.0.17
# jdk.jdi@11.0.17
# jdk.jdwp.agent@11.0.17
# jdk.jfr@11.0.17
# jdk.jlink@11.0.17
# jdk.jshell@11.0.17
# jdk.jsobject@11.0.17
# jdk.jstatd@11.0.17
# jdk.localedata@11.0.17
# jdk.management@11.0.17
# jdk.management.agent@11.0.17
# jdk.management.jfr@11.0.17
# jdk.naming.dns@11.0.17
# jdk.naming.ldap@11.0.17
# jdk.naming.rmi@11.0.17
# jdk.net@11.0.17
# jdk.pack@11.0.17
# jdk.rmic@11.0.17
# jdk.scripting.nashorn@11.0.17
# jdk.scripting.nashorn.shell@11.0.17
# jdk.sctp@11.0.17
# jdk.security.auth@11.0.17
# jdk.security.jgss@11.0.17
# jdk.unsupported@11.0.17
# jdk.unsupported.desktop@11.0.17
# jdk.xml.dom@11.0.17
# jdk.zipfs@11.0.17



# Define your base image
FROM ubuntu:lunar AS publish
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
