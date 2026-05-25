# ML Training Pipeline

## Overview

BloodLink uses **MobileNetV2** (transfer learning) to detect visual signs of pallor (anemia risk) and jaundice from smartphone photos of the eye. Models are trained on Google Colab and exported as TensorFlow Lite (`.tflite`) files for on-device inference.

---

## Models

| Model | File | Input | Output | Threshold |
|---|---|---|---|---|
| Pallor (anemia) | `pallor_model.tflite` | 224×224 RGB (lower eyelid crop) | Score [0.0–1.0] | > 0.70 → Possible sign |
| Jaundice | `jaundice_model.tflite` | 224×224 RGB (sclera crop) | Score [0.0–1.0] | > 0.35 → Possible sign |

---

## Datasets

### Pallor
- **Source:** [EYES-DEFY-ANEMIA](https://www.kaggle.com/datasets/harshwardhanfartale/eyes-defy-anemia) (Kaggle)
- **Size:** 218 conjunctiva images with hemoglobin (Hb) values
- **Labeling:** Hb < 12 g/dL (female) or < 13 g/dL (male) → positive; otherwise → negative
- **Augmentation:** Brightness jitter (±30%), rotation (±15°), horizontal flip → ~600 images after augmentation

### Jaundice
- **Source:** [Jaundice Image Data](https://www.kaggle.com/datasets/aiolapo/jaundice-image-data) (Kaggle)
- **Labeling:** Binary classification (jaundice / normal)
- **Augmentation:** Same pipeline as pallor

---

## Training Pipeline

### Prerequisites
- Google Colab (free GPU runtime)
- Kaggle API key for dataset download

### Steps
1. Open `train_pallor.ipynb` or `train_jaundice.ipynb` in Google Colab
2. Upload your Kaggle API key when prompted
3. Run all cells — the notebook will:
   - Download and preprocess the dataset
   - Apply data augmentation
   - Fine-tune MobileNetV2 (frozen base + custom classifier head)
   - Evaluate on validation set (accuracy, confusion matrix, ROC curve)
   - Export to `.tflite` format
4. Download the exported `.tflite` file
5. Place it in `app/src/main/assets/`
6. Rebuild the Android app

### Hyperparameters
| Parameter | Value |
|---|---|
| Base model | MobileNetV2 (ImageNet pretrained) |
| Input size | 224 × 224 × 3 |
| Frozen layers | All convolutional layers |
| Classifier head | GlobalAveragePooling2D → Dense(128, ReLU) → Dropout(0.3) → Dense(1, Sigmoid) |
| Optimizer | Adam (lr=0.001) |
| Loss | Binary crossentropy |
| Epochs | 30 (with early stopping, patience=5) |
| Batch size | 16 |
| Validation split | 20% |

---

## Data Augmentation

Applied during training to reduce overfitting and simulate real-world phone camera conditions:

| Augmentation | Range | Purpose |
|---|---|---|
| Brightness jitter | ±30% | Simulates indoor/outdoor lighting |
| Rotation | ±15° | Simulates tilted phone angles |
| Horizontal flip | 50% chance | Doubles effective dataset size |
| Zoom | ±10% | Simulates varying distances |

---

## Known Limitations

1. **Domain mismatch** — Training images were captured under controlled clinical conditions (Samsung S6 with macro attachment). Real-world phone photos differ in lighting, distance, and camera quality. This causes elevated false positive rates.

2. **Small dataset** — 218 images (pallor) is below the recommended minimum for robust clinical ML. Data augmentation helps but does not fully compensate.

3. **Recommended improvement** — Collect 40–60 images using the BloodLink app itself at a real blood donation event, validated against CBC hemoglobin results. Mix with existing dataset and retrain.

---

## Evaluation Metrics

After training, the notebook outputs:
- Training/validation accuracy and loss curves
- Confusion matrix (TP, TN, FP, FN)
- Sensitivity (recall) and specificity
- ROC curve with AUC score

Include these in your thesis methodology chapter.
