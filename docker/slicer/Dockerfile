FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    fuse3 \
    libegl1 \
    libfontconfig1 \
    libfreetype6 \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    libgtk-3-0 \
    libosmesa6 \
    libsm6 \
    libx11-6 \
    libxext6 \
    libxrender1 \
    xvfb \
    && rm -rf /var/lib/apt/lists/*

ARG ORCA_VERSION=v2.3.1
ARG SLICER_APPIMAGE_URL="https://github.com/SoftFever/OrcaSlicer/releases/download/${ORCA_VERSION}/OrcaSlicer_Linux_AppImage_${ORCA_VERSION}.AppImage"

WORKDIR /opt/slicer
RUN curl -fsSL "${SLICER_APPIMAGE_URL}" -o /tmp/slicer.AppImage \
    && chmod +x /tmp/slicer.AppImage \
    && /tmp/slicer.AppImage --appimage-extract \
    && mv squashfs-root /opt/slicer/app \
    && rm -f /tmp/slicer.AppImage \
    && ln -sf /opt/slicer/app/AppRun /usr/local/bin/orca-slicer \
    && ln -sf /opt/slicer/app/AppRun /usr/local/bin/bambu-studio

COPY src/main/resources/slicer-profiles/bambu-a1 /opt/slicer/profiles

RUN useradd --create-home --shell /bin/bash slicer \
    && mkdir -p /work \
    && chown -R slicer:slicer /opt/slicer /work

USER slicer
WORKDIR /work
ENV QT_QPA_PLATFORM=offscreen

ENTRYPOINT ["/usr/local/bin/bambu-studio"]
