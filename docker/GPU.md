# Soporte de GPU del pipeline de ML — registro de entorno

Verificado el 06/08/2026 en la máquina de referencia (Windows 11 + Docker
Desktop con backend WSL2 + RTX 3060 Ti 8 GB).

## Qué hizo falta instalar

**Nada.** Docker Desktop con backend WSL2 integra el runtime NVIDIA: el paso
de GPU al contenedor funcionó a la primera con `--gpus all`. No se instaló
NVIDIA Container Toolkit por separado, ni drivers, ni paquetes de WSL2.

## Versiones verificadas

| Componente | Versión | Cómo se fijó |
|---|---|---|
| Driver NVIDIA (host Windows) | 610.88 | preinstalado en la máquina |
| GPU | NVIDIA GeForce RTX 3060 Ti, 8 GiB | hardware |
| Imagen de entrenamiento GPU | `pytorch/pytorch:2.6.0-cuda12.4-cudnn9-runtime` | `docker/ml-gpu.Dockerfile` (digest fijado por tag oficial) |
| torch / torchvision | 2.6.0 / 0.21.0 (CUDA 12.4, cuDNN 9) | incluidos en la imagen |
| Resto de dependencias | `docker/ml.requirements.txt` (sin `ai-edge-torch`) | ver comentario del Dockerfile |
| Imagen de verificación | `nvidia/cuda:12.4.1-base-ubuntu22.04` | solo para el smoke de GPU |

## Verificación reproducible

```bash
docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
```

Salida esperada: la RTX 3060 Ti listada. Después:

```bash
docker compose -f docker-compose.gpu.yml -p botabien-ml-gpu build ml-gpu
docker compose -f docker-compose.gpu.yml -p botabien-ml-gpu run --rm ml-gpu
```

El comando por defecto de la imagen imprime el nombre de la GPU visto por
torch (`torch.cuda.get_device_name(0)`).

## Notas de rendimiento

- Los entrenadores usan AMP (float16) — en Ampere acelera y deja holgura en
  los 8 GB; batch por defecto 64 en GPU.
- Si la GPU queda ociosa (dataloader como cuello de botella por el mount de
  Windows → WSL), subir `--workers` y valorar copiar `ml/data/` al
  filesystem de WSL. Medirlo con la utilización de `nvidia-smi`, no a ojo.
