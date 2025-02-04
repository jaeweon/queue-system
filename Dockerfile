FROM openjdk:17-jdk

COPY build/libs/*SNAPSHOT.jar /app.jar

EXPOSE 9010

CMD ["java", "-jar", "app.jar"]
