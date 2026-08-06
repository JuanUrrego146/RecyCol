# Entorno GPU del pipeline de ML. Mismo torch 2.6.0 que la imagen CPU
# (docker/ml.Dockerfile), con CUDA 12.4 + cuDNN 9 fijados por la imagen
# oficial de PyTorch — versiones inmutables para que el build sea reproducible.
# Requiere Docker con runtime NVIDIA (Docker Desktop + WSL2 lo trae) y
# lanzarse con GPU (overlay docker-compose.gpu.yml).
FROM pytorch/pytorch:2.6.0-cuda12.4-cudnn9-runtime

COPY ml.requirements.txt /tmp/ml.requirements.txt

# ai-edge-torch se excluye aquí: su cadena de dependencias reinstalaría torch
# CPU encima del torch CUDA de la imagen. La exportación de S27 corre en el
# contenedor CPU, donde ese paquete sí está.
RUN grep -v "ai-edge-torch" /tmp/ml.requirements.txt > /tmp/ml.requirements.gpu.txt \
    && pip install --no-cache-dir -r /tmp/ml.requirements.gpu.txt

WORKDIR /workspace/ml
CMD ["python", "-c", "import torch; print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'sin GPU')"]
