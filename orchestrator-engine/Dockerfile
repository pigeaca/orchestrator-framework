FROM amazoncorretto:21-alpine

WORKDIR /app

COPY build/libs/orchestrator-engine.jar orchestrator-engine.jar

ENTRYPOINT ["java", "-jar", "orchestrator-engine.jar"]