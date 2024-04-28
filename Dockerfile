# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-alpine
# WORKDIR /src
# COPY . .
# RUN go mod download
# RUN go build -o /bin/client ./cmd/client
# RUN go build -o /bin/server ./cmd/server
ENTRYPOINT [ "/bin/server" ]