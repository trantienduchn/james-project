# Generating keystore file

```
openssl req -x509 -nodes -sha256 -newkey rsa:2048 -keyout james.key -out james.crt -subj "/CN=james2.local"
keytool -import -v -trustcacerts -file james.crt -keystore keystore-smtp2 -keypass james72laBalle -storepass james72laBalle -alias james
keytool -importkeystore -srckeystore keystore-smtp2 -srcstoretype pkcs12 -srcstorepass james72laBalle -srcalias james -destkeystore keystore-smtp2 -deststoretype jks -deststorepass james72laBalle -destalias james
```

copy the file into `conf/` directory

# Setup
 - two james
 - one james on the host `james.local`
 - one james on the host `james2.local`
 - configure smtp servers on two james to use the keystore `keystore-smtp2` 
 - configure james on the host `james.local` to use `RemoteDelivery` with `<mail.smtp.ssl.enable>true</mail.smtp.ssl.enable>` and `<mail.smtp.port>465</mail.smtp.port>` 
## Run

```
docker-compose up
```
## Scenario

Login as `user@james.local` and send an email to `user@james2.local`.

# Reports

## Report 1

Seem like the way creating the `keystore-smtp2` is not correct. There is an exception thrown during handshake on `james2.local`:
```
james2           | javax.net.ssl.SSLHandshakeException: no cipher suites in common
james2           | 	at sun.security.ssl.Alerts.getSSLException(Alerts.java:192)
james2           | 	at sun.security.ssl.SSLEngineImpl.fatal(SSLEngineImpl.java:1647)
james2           | 	at sun.security.ssl.Handshaker.fatalSE(Handshaker.java:318)
james2           | 	at sun.security.ssl.Handshaker.fatalSE(Handshaker.java:306)
james2           | 	at sun.security.ssl.ServerHandshaker.chooseCipherSuite(ServerHandshaker.java:1127)
james2           | 	at sun.security.ssl.ServerHandshaker.clientHello(ServerHandshaker.java:814)
james2           | 	at sun.security.ssl.ServerHandshaker.processMessage(ServerHandshaker.java:221)
james2           | 	at sun.security.ssl.Handshaker.processLoop(Handshaker.java:1037)
james2           | 	at sun.security.ssl.Handshaker$1.run(Handshaker.java:970)
james2           | 	at sun.security.ssl.Handshaker$1.run(Handshaker.java:967)
james2           | 	at java.security.AccessController.doPrivileged(Native Method)
james2           | 	at sun.security.ssl.Handshaker$DelegatedTask.run(Handshaker.java:1459)
james2           | 	at org.jboss.netty.handler.ssl.SslHandler.runDelegatedTasks(SslHandler.java:1393)
james2           | 	at org.jboss.netty.handler.ssl.SslHandler.unwrap(SslHandler.java:1256)
james2           | 	... 18 common frames omitted
```

But still can receive the email on `james2.local` side. 
IDK whether some fallback actions in James trying to deliver the mail through smtp port 25 or the SSL remote delivery did it.

Here is a part of logs in `james.local`:
```
DEBUG: getProvider() returning javax.mail.Provider[TRANSPORT,smtp,com.sun.mail.smtp.SMTPTransport,Oracle]
DEBUG SMTP: useEhlo true, useAuth false
DEBUG SMTP: trying to connect to host "172.22.0.5", port 465, isSSL true
11:10:19.391 [INFO ] o.a.j.t.m.r.d.MailDelivrer - Could not connect to SMTP host: 172.22.0.5, port: 465
```
