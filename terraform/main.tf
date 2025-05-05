# terraform/main.tf

provider "google" {
  project = var.project_id
  region  = var.region
}

# GCS Bucket for Solar Data Archives
resource "google_storage_bucket" "archive_bucket" {
  name          = var.archive_bucket_name
  location      = var.region
  storage_class = var.archive_bucket_storage_class
  force_destroy = false # Set to true only if you are sure during testing, false is safer

  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  # Lifecycle rules to manage storage costs
  dynamic "lifecycle_rule" {
    # Apply rules only if delete_days is greater than 0
    for_each = var.archive_bucket_lifecycle_delete_days > 0 ? [1] : []
    content {
      action {
        type = "Delete"
      }
      condition {
        age = var.archive_bucket_lifecycle_delete_days
      }
    }
  }

  dynamic "lifecycle_rule" {
    # Apply rules only if coldline_days is greater than 0
    for_each = var.archive_bucket_lifecycle_coldline_days > 0 ? [1] : []
    content {
      action {
        type          = "SetStorageClass"
        storage_class = "COLDLINE"
      }
      condition {
        age = var.archive_bucket_lifecycle_coldline_days
        # Only apply if the current class isn't already COLDLINE or ARCHIVE
        matches_storage_class = ["STANDARD", "NEARLINE"]
      }
    }
  }

  labels = {
    purpose = "solar-data-archive"
    managed-by = "terraform"
  }
}

# Firestore Database Instance (Native Mode)
resource "google_project_service" "firestore_api" {
  # Explicitly enable the Firestore API if not already done
  # Although we enabled it via gcloud, this makes TF aware
  service            = "firestore.googleapis.com"
  disable_on_destroy = false # Keep API enabled even if TF destroys resources
}

resource "google_firestore_database" "database" {
  # Ensure Firestore API is enabled before creating the database
  depends_on = [google_project_service.firestore_api]

  project                   = var.project_id
  name                      = "(default)" # Standard name for the default Firestore DB
  location_id               = var.firestore_location_id
  type                      = "FIRESTORE_NATIVE" # Or "DATASTORE_MODE" if needed
  app_engine_integration_mode = "DISABLED" # Typically disabled unless using App Engine legacy features
  delete_protection_state   = "DELETE_PROTECTION_DISABLED" # Or ENABLED for production safety
  point_in_time_recovery_enablement = "POINT_IN_TIME_RECOVERY_ENABLED" # Recommended
}

# Secret Manager Secret for Solarman Token
resource "google_secret_manager_secret" "solarman_token" {
  secret_id = var.solarman_secret_name
  project   = var.project_id

  replication {
    auto {}
  }

  labels = {
    purpose = "solarman-api-token"
    managed-by = "terraform"
  }
}

# Note: We define the secret container here, but not its value (version).
# The value should be added securely outside of Terraform code.
# You can add the first version manually via GCP Console or using gcloud:
# echo -n "YOUR_SOLARMAN_TOKEN" | gcloud secrets versions add solarman-bearer-token --data-file=- --project=solarman-data-ingestor
