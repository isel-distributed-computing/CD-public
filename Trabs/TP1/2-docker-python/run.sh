#!/bin/sh
docker run --rm --network none --memory 256m --memory-swap 256m \
  --user "$(id -u):$(id -g)" -v "$PWD":/work python:3.12-slim \
  sh -c 'timeout 10 python /work/prog.py < /work/input.txt > /work/out.txt 2> /work/err.txt; echo $? > /work/exit.txt'