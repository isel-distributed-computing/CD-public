@echo off
docker run -d --rm --name redis-dev -p 127.0.0.1:6379:6379 redis:8
