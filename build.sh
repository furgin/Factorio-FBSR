#!/bin/sh

VERSION=0.8

ID=`docker images registry.furgin.org/furgin/factorio-fbsr:$VERSION --format "{{.ID}}"`

if [ ! -z "$ID" ]; then
    echo "Docker tag for version $VERSION already exists with ID: $ID"
    exit
fi

DIR=`pwd`
cd $DIR/FactorioBlueprintStringRenderer
mvn clean package
cd $DIR

docker build -t factorio-fbsr .
docker tag factorio-fbsr registry.furgin.org/furgin/factorio-fbsr:$VERSION
docker tag factorio-fbsr registry.furgin.org/furgin/factorio-fbsr:latest

docker push registry.furgin.org/furgin/factorio-fbsr:$VERSION
docker push registry.furgin.org/furgin/factorio-fbsr:latest
