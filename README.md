# snowflake-id-service


## Runtime 

Runtime dependencies
```shell
./gradlew clean bootJar -x test
```

```shell
docker build -t snowflake-id-service .
```
```shell
docker image tag snowflake-id-service ymoroz/snowflake-id-service:latest
docker push ymoroz/snowflake-id-service:latest
```
```shell
kind load docker-image ymoroz/snowflake-id-service:latest
```

```shell
helm package .
```

```shell
helm install snowflake-id-service ./snowflake-id-service-0.1.0.tgz
```