# Imagem de produção. Recebe o jar já construído e testado (./mvnw verify ou o job "build" do CI);
# não compila nada aqui, para a imagem ser exatamente o artefato que passou nos testes.
#
#   ./mvnw -DskipTests package && docker build -t poe2buildassistant .
FROM eclipse-temurin:25-jre

# Usuário sem privilégios: o processo não roda como root dentro do container.
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app

COPY --chown=app:app target/Poe2BuildAssistant-*.jar app.jar

USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

# Sem curl na imagem: o próprio bash faz o GET em /api/health.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8080} && printf 'GET /api/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"ok\"' <&3"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
