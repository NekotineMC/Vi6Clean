# syntax=docker/dockerfile:1

#FROM eclipse-temurin:21-alpine AS buildPlugin
#COPY . .
#RUN ./gradlew build
# RUN go build -o /bin/client ./cmd/client
# RUN go build -o /bin/server ./cmd/server

FROM eclipse-temurin:21-alpine AS runServer
ADD https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/496/downloads/paper-1.20.4-496.jar paper-server.jar
COPY eula.txt .
RUN echo stop | java -jar paper-server.jar

FROM eclipse-temurin:21-alpine
COPY eula.txt .
COPY ops.json .
COPY server.properties .
COPY server-icon.png .
COPY world/ .world/
COPY plugins/ plugins/
COPY --from=runServer cache/ cache/
COPY --from=runServer libraries/ libraries/
COPY --from=runServer versions/ versions
COPY --from=runServer paper-server.jar .

EXPOSE 25565/tcp
EXPOSE 25565/udp
ENTRYPOINT [ "java", "-jar", "paper-server.jar", "nogui" ]