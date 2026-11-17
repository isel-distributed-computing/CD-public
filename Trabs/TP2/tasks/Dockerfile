FROM python:3.12-slim

# relative paths passed by the worker are resolved from /data
WORKDIR /data

COPY tasks.py /opt/tp2-tasks/

ENTRYPOINT ["python3", "/opt/tp2-tasks/tasks.py"]
CMD ["--help"]
