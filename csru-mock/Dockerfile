# IS CSRÚ mock – čistá Java 21, bez Mavenu a závislostí.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY src ./src
# --selftest: všetky demo odpovede musia zodpovedať XSD z integračného manuálu, inak build zlyhá
RUN mkdir -p out && javac -d out $(find src/main/java -name '*.java') \
    && cp -r src/main/resources/* out/ \
    && java -cp out sk.posam.sos.csru.CsruMockServer --selftest \
    && jar --create --file csru-mock.jar --main-class sk.posam.sos.csru.CsruMockServer -C out .

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/csru-mock.jar app.jar
EXPOSE 8091
ENV PORT=8091
ENTRYPOINT ["java", "-Dstdout.encoding=UTF-8", "-jar", "app.jar"]
