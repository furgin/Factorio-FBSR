FROM openjdk:8
WORKDIR /opt/factorio-fbsr
COPY FactorioBlueprintStringRenderer/target/FactorioBlueprintStringRenderer-0.0.1-SNAPSHOT-jar-with-dependencies.jar .
EXPOSE 8080
CMD java -jar FactorioBlueprintStringRenderer-0.0.1-SNAPSHOT-jar-with-dependencies.jar
