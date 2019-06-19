# Run James
#
# VERSION	1.0

FROM openjdk:8u181-jre

# Ports that are used
#
# 25  SMTP without authentication
# 110 POP3
# 143 IMAP with startTLS enabled
# 465 SMTP with authentication and socketTLS enabled
# 587 SMTP with authentication and startTLS enabled
# 993 IMAP with socketTLS enabled

EXPOSE 25 110 143 465 587 993 4000

WORKDIR /root

# Get data we need to run James : build results and configuration
ADD destination/james-server-app-3.4.0-SNAPSHOT-app.zip /root/james-server-app-3.4.0-SNAPSHOT-app.zip
ADD destination/conf /root/conf
ADD destination/glowroot/plugins /root/glowroot/plugins
ADD destination/glowroot/glowroot.jar /root/glowroot/glowroot.jar
ADD destination/glowroot/lib/glowroot-embedded-collector.jar /root/glowroot/lib/glowroot-embedded-collector.jar
ADD destination/glowroot/lib/glowroot-logging-logstash.jar /root/glowroot/lib/glowroot-logging-logstash.jar
ADD destination/glowroot/admin.json /root/glowroot/admin.json

VOLUME /logs
VOLUME /root/conf
VOLUME /root/glowroot/plugins

# Unzip build result
RUN unzip james-server-app-3.4.0-SNAPSHOT-app.zip

# Copy configuration.
# Warning : we want to use the wrapper.conf file we just compiled.
RUN cp james-server-app-3.4.0-SNAPSHOT/conf/wrapper.conf .
RUN rm -r james-server-app-3.4.0-SNAPSHOT/conf/*
RUN cp -r conf/* james-server-app-3.4.0-SNAPSHOT/conf
RUN cp wrapper.conf james-server-app-3.4.0-SNAPSHOT/conf

ENV PATH "$PATH:/root/glowroot/lib"

WORKDIR /root/james-server-app-3.4.0-SNAPSHOT/bin
ENTRYPOINT ["./wrapper-linux-x86-64","../conf/wrapper.conf", "wrapper.syslog.ident=james", "wrapper.pidfile=../var/james.pid", "wrapper.daemonize=FALSE", "wrapper.java.additional.1=-javaagent:/root/glowroot.jar"]
