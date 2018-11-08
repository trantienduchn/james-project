ARG BASE=linagora/james-project
ARG TAG=latest
FROM ${BASE}:${TAG} as source

FROM debian:8.11

RUN apt-get update
RUN apt-get install -y ruby-dev ruby build-essential
RUN gem install fpm
RUN apt-get install -y rpm

ADD scripts/ /packages

COPY scripts/james.service /packages/package/usr/share/james/
COPY --from=source /root/james-server.jar /packages/package/usr/share/james/james-withoutldap-server.jar
COPY --from=source /root/james-cli.jar /packages/package/usr/share/james/james-cli.jar
COPY --from=source /root/james-server-cassandra-rabbitmq-guice.lib/ /packages/package/usr/share/james/james-server-cassandra-rabbitmq-guice.lib/
COPY --from=source /root/james-server-cli.lib/ /packages/package/usr/share/james/james-server-cli.lib/

RUN mkdir -p /packages/package/var/lib/james
RUN mkdir -p /packages/package/var/log/james

ARG RELEASE
ENV RELEASE $RELEASE
ARG ITERATION
ENV ITERATION $ITERATION

WORKDIR /packages

ENTRYPOINT [ "sh", "-c", "/packages/package.sh $RELEASE $ITERATION" ]
