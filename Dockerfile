FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/multi-mode-delivery-dispatch /app
ENV DISPATCH_PORT=50051
EXPOSE 50051
ENTRYPOINT ["/app/bin/multi-mode-delivery-dispatch"]
