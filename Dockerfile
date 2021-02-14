FROM adoptopenjdk/openjdk11-openj9:jdk-11.0.1.13-alpine-slim
RUN apk --no-cache add curl
COPY target/oan-staking-dashboard-api-*.jar oan-staking-dashboard-api.jar
EXPOSE 8080
CMD java -Dcom.sun.management.jmxremote -noverify ${JAVA_OPTS} -jar oan-staking-dashboard-api.jar
