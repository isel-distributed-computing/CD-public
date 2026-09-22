FROM eclipse-temurin:21.0.12_8-jre-noble
RUN mkdir /usr/executables
COPY ServiceREST.jar  /usr/executables
RUN mkdir /usr/datafiles
WORKDIR  /usr/datafiles
ENTRYPOINT ["java", "-jar", "/usr/executables/ServiceREST.jar"]