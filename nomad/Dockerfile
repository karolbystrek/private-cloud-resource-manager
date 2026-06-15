FROM ubuntu:22.04

ARG NOMAD_VERSION=1.7.7
ARG NOMAD_DEVICE_NVIDIA_VERSION=1.1.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget unzip ca-certificates \
    && wget -q "https://releases.hashicorp.com/nomad/${NOMAD_VERSION}/nomad_${NOMAD_VERSION}_linux_amd64.zip" \
         -O /tmp/nomad.zip \
    && unzip /tmp/nomad.zip -d /usr/local/bin \
    && wget -q "https://releases.hashicorp.com/nomad-device-nvidia/${NOMAD_DEVICE_NVIDIA_VERSION}/nomad-device-nvidia_${NOMAD_DEVICE_NVIDIA_VERSION}_linux_amd64.zip" \
         -O /tmp/nomad-device-nvidia.zip \
    && mkdir -p /nomad/plugins \
    && unzip /tmp/nomad-device-nvidia.zip -d /nomad/plugins \
    && chmod +x /usr/local/bin/nomad /nomad/plugins/nomad-device-nvidia \
    && rm /tmp/nomad.zip /tmp/nomad-device-nvidia.zip /nomad/plugins/LICENSE.txt \
    && apt-get purge -y wget unzip \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 4646 4647 4648

ENTRYPOINT ["/usr/local/bin/nomad"]
CMD ["agent", "-config=/etc/nomad.d/local-dev.hcl"]
