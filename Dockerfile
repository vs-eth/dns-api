FROM eu.gcr.io/vseth-public/base:delta

RUN apt install -y --no-install-recommends default-jdk maven

COPY src src
COPY servis servis
COPY pom.xml pom.xml

RUN mvn package

FROM eu.gcr.io/vseth-public/base:delta

RUN apt install -y --no-install-recommends default-jre-headless

COPY --from=0 /app/target/dnsapi-2.0.jar /app/dns-api.jar
COPY cinit.yml /etc/cinit.d/dns-api.yml
COPY dns-api.properties /app
