FROM openjdk:17-slim

# Install FFmpeg
RUN apt update && \
    apt install -y ffmpeg && \
    apt clean

WORKDIR /app
COPY mediaserver-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]