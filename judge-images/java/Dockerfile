FROM eclipse-temurin:21-jdk

RUN groupadd --system --gid 10001 judge \
    && useradd --system --uid 10001 --gid judge --no-create-home --home-dir /nonexistent \
       --shell /usr/sbin/nologin judge \
    && mkdir -p /input /out \
    && chown root:root /input && chmod 0755 /input \
    && chown judge:judge /out && chmod 0700 /out

COPY judge-run /usr/local/bin/judge-run
RUN chmod 0755 /usr/local/bin/judge-run

USER 10001:10001
WORKDIR /tmp
