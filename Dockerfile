FROM eu.gcr.io/vseth-public/base:delta

RUN apt install default-jdk maven

COPY src src
COPY pom.xml pom.xml

RUN mvn package



FROM eu.gcr.io/vseth-public/base:delta

RUN apt install -y default-jre-headless

COPY --from=0 /app/target/dnsapi-2.0.jar /app/dns-api.jar
COPY cinit.yml /etc/cinit.d/dns-api.yml
COPY dns-api.properties /app
