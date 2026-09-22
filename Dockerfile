# Stage 1: The Builder
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Dependency Caching Layer
# Copy ONLY pom.xml first & download dependencies.
# Docker caches this layer. Unless change pom.xml,
# subsequent builds skip this step & use cached downloads.
COPY pom.xml ./
RUN mvn dependency:go-offline

# Source Code Layer
# Now copy actual code & build JAR.
COPY src/ ./src
RUN mvn clean package -DskipTests

# Stage 2: The Runtime (The Final Image)
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Security: Non-Root User
# By default, containers run as root. Create restricted system user
# so if JVM is ever compromised, attacker has no OS-level privileges.
RUN groupadd -r velocity && useradd -r -g velocity velocity
USER velocity

# Fail-Safe Configuration
# Hardcode production profile so this container can NEVER accidentally
# boot into "dev" profile & try to hit localhost.
ENV SPRING_PROFILES_ACTIVE=prod

# Extract Artifact
# Copy ONLY finished JAR from 'builder' stage.
# Leave behind JDK, Maven, and all source code.
COPY --from=builder /app/target/*.jar velocity-api.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "velocity-api.jar"]