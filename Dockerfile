# ---------- Etapa 1: build com Maven ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Copia primeiro o pom.xml para aproveitar o cache das dependências
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Copia o código e gera o .jar
COPY src ./src
RUN mvn -q -DskipTests clean package

# ---------- Etapa 2: imagem final, só com o Java (JRE) ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# O Render informa a porta na variável PORT (localmente, 8080)
ENV PORT=8080
# Plano gratuito do Render tem 512 MB: limita o heap a 75% da memória do contêiner
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar"]
