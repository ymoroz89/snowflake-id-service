FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY snowflake-server/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java","-jar","app.jar"]
