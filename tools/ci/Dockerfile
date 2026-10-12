# The Linux CI runner in a box, for reproducing the Linux job locally. See linux-check.sh.
FROM ubuntu:24.04
COPY linux-packages.txt /tmp/linux-packages.txt
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openjdk-21-jdk ca-certificates $(cat /tmp/linux-packages.txt) \
    && apt-get clean
ENTRYPOINT ["/src/tools/ci/linux-check.sh"]
