ARG image
ARG cuda_ver=12.2.0
ARG cuda_distro=ubuntu20.04

FROM nvidia/cuda:${cuda_ver}-devel-${cuda_distro} as cuda
FROM $image

# Install cuda headers https://github.com/eclipse/openj9/blob/master/buildenv/docker/mkdocker.sh#L586-L593
RUN mkdir -p /usr/local/cuda/nvvm
# TEMP: Copy the header files from the local compile box to the container.
COPY --from=cuda /usr/local/cuda/include         /usr/local/cuda/include
COPY --from=cuda /usr/local/cuda/nvvm/include    /usr/local/cuda/nvvm/include

ENV CUDA_HOME="/usr/local/cuda"