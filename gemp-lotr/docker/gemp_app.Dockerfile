FROM bellsoft/liberica-openjdk-alpine:27

RUN apk add --no-cache \
    procps \
    net-tools \
    iputils \
    bash \
    util-linux \
    dpkg \
    gzip \
    curl \
    tar \
    binutils \
    coreutils \
    freetype \
    fontconfig \
    git \
    nano \
    openrc \
    apache2

ENV MAVEN_CONFIG=/root/.m2

ARG MAVEN_VERSION=3.10.0
ARG MAVEN_SHA512=908b1501bfb420bf7c8affb855534a9c407fd6099367bfb9f2f2dcb8e9799102bffb84518cde74c679bd76870247c6528683abdd620581bffa90f95d92d175aa

ENV MAVEN_HOME=/opt/apache-maven-${MAVEN_VERSION}

RUN curl --fail --location --silent --show-error \
      "https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      --output /tmp/apache-maven.tar.gz \
    && echo "${MAVEN_SHA512}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
    && tar -xzf /tmp/apache-maven.tar.gz -C /opt \
    && ln -s "${MAVEN_HOME}/bin/mvn" /usr/local/bin/mvn \
    && rm /tmp/apache-maven.tar.gz

# Enables the JRE remote debugging; perhaps comment this out in a production build
#ENV JAVA_TOOL_OPTIONS -agentlib:jdwp=transport=dt_socket,address=*:8000,server=y,suspend=n

WORKDIR /etc/gemp-lotr
