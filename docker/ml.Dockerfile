# Entorno del pipeline de ML de BotaBien (módulo ml/, Python puro, aislado de la app).
# PyTorch en variante CPU: el entrenamiento pesado corre en Colab/Kaggle según el README;
# este contenedor cubre preparación de datos, síntesis, exportación y pruebas locales.
FROM python:3.11-slim

COPY ml.requirements.txt /tmp/ml.requirements.txt

RUN pip install --no-cache-dir \
        torch==2.6.0 torchvision==0.21.0 \
        --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r /tmp/ml.requirements.txt

WORKDIR /workspace/ml
CMD ["python", "--version"]
