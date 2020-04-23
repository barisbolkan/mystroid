FROM openjdk:14-jdk-alpine
RUN apk update && apk add bash

WORKDIR /opt/mystroid-api

COPY ./mystroid-grpc-auth.json /opt/mystroid-api/mystroid-grpc-auth.json
ENV GOOGLE_APPLICATION_CREDENTIALS=/opt/mystroid-api/mystroid-grpc-auth.json

ADD --chown=daemon:daemon target/docker/stage/opt /opt
USER daemon

EXPOSE 8080 5005

ENTRYPOINT ["/opt/mystroid-api/bin/mystroid-api"]

CMD []
