# syntax=docker/dockerfile:1

#FROM eclipse-temurin:21-alpine AS buildPlugin
#COPY . .
#RUN ./gradlew build
# RUN go build -o /bin/client ./cmd/client
# RUN go build -o /bin/server ./cmd/server

FROM eclipse-temurin:21-alpine AS runServer
ADD https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/496/downloads/paper-1.20.4-496.jar paper-server.jar
RUN echo stop | java -jar paper-server.jar

FROM eclipse-temurin:21-alpine
COPY Docker/eula.txt minecraft/
COPY Docker/ops.json minecraft/
COPY Docker/server.properties minecraft/
COPY Docker/server-icon.png minecraft/
COPY Docker/world/ minecraft/world/
COPY Docker/plugins/ minecraft/plugins/
COPY build/libs/Vi6Clean-0.0.1-coreShaded.jar minecraft/plugins/
COPY --from=runServer cache/ minecraft/cache/
COPY --from=runServer libraries/ minecraft/libraries/
COPY --from=runServer versions/ minecraft/versions/
COPY --from=runServer paper-server.jar minecraft/

EXPOSE 25565/tcp
EXPOSE 25565/udp
ENTRYPOINT [ "java", "-jar", "paper-server.jar", "nogui" ]