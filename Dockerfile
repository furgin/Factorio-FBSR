FROM openjdk:8
COPY FactorioBlueprintStringRenderer/target/FactorioBlueprintStringRenderer-0.0.1-SNAPSHOT-jar-with-dependencies.jar .
COPY config.json .
CMD java -jar FactorioBlueprintStringRenderer-0.0.1-SNAPSHOT-jar-with-dependencies.jar
