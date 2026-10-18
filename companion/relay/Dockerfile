# lani-relay (docs/plans/05-built/relay.md). The family page's files are the bridge's, so the build context is companion/:
#   docker build -f companion/relay/Dockerfile -t lani-relay companion
#   docker run -p 8080:8080 -v lani-relay:/data lani-relay
# No dependencies at run time: Bun and its SQLite.
FROM oven/bun:1.3-alpine
WORKDIR /app
COPY relay/package.json ./
COPY relay/src ./src
COPY bridge/web/family ./web/family
ENV RELAY_HOST=0.0.0.0 \
    RELAY_PORT=8080 \
    RELAY_DATA_DIR=/data \
    RELAY_WEB_DIR=/app/web/family
RUN mkdir -p /data && chown bun:bun /data
USER bun
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/health >/dev/null || exit 1
CMD ["bun", "src/index.ts"]
