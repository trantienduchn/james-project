Generating keystore file:

```
openssl req -x509 -nodes -sha256 -newkey rsa:2048 -keyout james.key -out james.crt -subj "/CN=james2.local"
keytool -import -v -trustcacerts -file james.crt -keystore keystore-smtp2 -keypass james72laBalle -storepass james72laBalle -alias james
keytool -importkeystore -srckeystore keystore-smtp2 -srcstoretype pkcs12 -srcstorepass james72laBalle -srcalias james -destkeystore keystore-smtp2 -deststoretype jks -deststorepass james72laBalle -destalias james
```

copy the file into `conf/` directory