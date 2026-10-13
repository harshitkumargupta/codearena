FROM python:3.12-slim

RUN groupadd --system --gid 10001 judge \
    && useradd --system --uid 10001 --gid judge --no-create-home --home-dir /nonexistent \
       --shell /usr/sbin/nologin judge \
    && mkdir -p /input \
    && chown root:root /input && chmod 0755 /input

COPY judge-run /usr/local/bin/judge-run
RUN chmod 0755 /usr/local/bin/judge-run

USER 10001:10001
WORKDIR /tmp
