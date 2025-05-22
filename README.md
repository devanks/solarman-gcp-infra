# ☀️ Solarman GCP Infrastructure

Welcome to the **Solarman GCP Infrastructure** project! This repository orchestrates a robust, cloud-native pipeline for ingesting, processing, and archiving solar energy data using Google Cloud Platform (GCP) services and modern Java microservices. 

---

## 🏗️ Architecture Overview

```mermaid
graph TD
    A[Solarman API ☀️] -->|Fetch Data| B(Ingestor Function)
    B -->|Store| C[Firestore DB]
    B -->|Secrets| D[Secret Manager]
    C -->|Read| E[Archiver Function]
    E -->|Archive| F[GCS Bucket]
    E -->|Logs| G[Cloud Logging]
```

- **Ingestor Function**: Fetches data from Solarman API, stores
