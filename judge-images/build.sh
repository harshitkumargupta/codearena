#!/bin/sh
set -eu
cd "$(dirname "$0")"
docker build -t codearena/judge-java:latest -f java/Dockerfile .
docker build -t codearena/judge-python:latest -f python/Dockerfile .
