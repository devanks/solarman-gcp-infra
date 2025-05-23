# --- Archive Function Resources ---

# 1. GCS Bucket for Firestore Archives
resource "google_storage_bucket" "firestore_archives_bucket" {
  project                     = var.project_id
  name                        = "${var.project_id}-firestore-archives" # Choose a unique bucket name solarman-data-ingestor-firestore-archives
  location                    = var.region                             # Or multi-region if preferred
  uniform_bucket_level_access = true
  force_destroy               = false # Set to true only for non-production cleanup

  # # Lifecycle rule to transition/delete old archives if needed (optional)
  # lifecycle_rule {
  #   action {
  #     type = "Delete" # Or "SetStorageClass" to move to cheaper storage
  #   }
  #   condition {
  #     age = 365 # Example: Delete archives older than 1 year
  #   }
  # }
  #
  # # Lifecycle rules to manage storage costs
  # dynamic "lifecycle_rule" {
  #   # Apply rules only if delete_days is greater than 0
  #   for_each = var.archive_bucket_lifecycle_delete_days > 0 ? [1] : []
  #   content {
  #     action {
  #       type = "Delete"
  #     }
  #     condition {
  #       age = var.archive_bucket_lifecycle_delete_days
  #     }
  #   }
  # }

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
    purpose    = "firestore-archive-storage"
    managed-by = "terraform"
  }
}

# 2. Service Account for the Archive Function
resource "google_service_account" "archive_function_sa" {
  project      = var.project_id
  account_id   = "firestore-archiver-sa"
  display_name = "SA for Firestore Archiver Function"
  description  = "Service account used by the Firestore archiver function"
}

# 3. IAM Permissions for Archive SA

# Allow reading and DELETING from Firestore (use datastore.user cautiously)
# Consider if a more specific role exists or if custom role is needed, but datastore.user is common
resource "google_project_iam_member" "archive_sa_firestore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.archive_function_sa.email}"
}

# Allow writing objects to the specific archive bucket
resource "google_storage_bucket_iam_member" "archive_sa_storage_writer" {
  bucket = google_storage_bucket.firestore_archives_bucket.name
  role   = "roles/storage.objectCreator" # Allows creating objects
  member = "serviceAccount:${google_service_account.archive_function_sa.email}"
  # Add roles/storage.objectViewer if the function needs to read/check existence
}

# Allow writing logs
resource "google_project_iam_member" "archive_sa_logs_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.archive_function_sa.email}"
}


# 4. Archive Function Source Code Archive (Java)
data "archive_file" "archiver_source_zip" {
  type        = "zip"
  source_dir  = "../functions/archiver-java" # New directory for Java archiver
  output_path = "${path.cwd}/.terraform/build/firestore-archiver-java-source-${timestamp()}.zip"
  excludes = [
    ".git/**",
    ".gradle/**",
    "build/**",
    ".idea/**",
    "*.iml",
    ".DS_Store",
    ".env"
  ]
}

# 5. Upload Archive Function ZIP (Java)
# (This resource remains the same, just uses the output of the new archive_file)
resource "google_storage_bucket_object" "archiver_function_source_zip" {
  name       = basename(data.archive_file.archiver_source_zip.output_path)
  bucket     = google_storage_bucket.function_code_bucket.name
  source     = data.archive_file.archiver_source_zip.output_path
  depends_on = [data.archive_file.archiver_source_zip]
}

# 6. Cloud Function (2nd Gen) - Archiver (Java)
resource "google_cloudfunctions2_function" "archiver_function" {
  project  = var.project_id
  name     = "firestore-archiver-java" # Consider suffixing with -java
  location = var.region

  build_config {
    runtime     = "java21"                                                        # Or your preferred Java version (e.g., java21)
    entry_point = "org.springframework.cloud.function.adapter.gcp.GcfJarLauncher" # Standard for Spring Cloud Function
    # Source upload using buildpacks (GCP default for Java source)
    source {
      storage_source {
        bucket = google_storage_bucket_object.archiver_function_source_zip.bucket
        object = google_storage_bucket_object.archiver_function_source_zip.name
      }
    }
  }

  service_config {
    max_instance_count    = 1
    min_instance_count    = 0
    available_memory      = "1Gi"
    timeout_seconds       = 540
    service_account_email = google_service_account.archive_function_sa.email
    # ingress_settings      = "ALLOW_INTERNAL_ONLY" # Change back after testing
    ingress_settings               = "ALLOW_ALL"
    all_traffic_on_latest_revision = true
    environment_variables = {
      MAIN_CLASS = "dev.devanks.solarman.archiver.ArchiverApplication"
      ARCHIVE_BUCKET_NAME : google_storage_bucket.firestore_archives_bucket.name
      ARCHIVE_DAYS_OLD : "0" # 0 Means the present day only
      ARCHIVE_DELETION_ENABLED : "true"
      LOGGING_LEVEL_DEV_DEVANKS_SOLARMAN_ARCHIVER : "DEBUG",
      LOGGING_LEVEL_ROOT : "INFO"
    }
  }
  depends_on = [
    google_storage_bucket_object.archiver_function_source_zip,
    google_project_iam_member.archive_sa_firestore_user,
    google_storage_bucket_iam_member.archive_sa_storage_writer,
    google_project_iam_member.archive_sa_logs_writer,
  ]
}

# infra/main.tf
# (Inside Archive Function Resources section or separate Scheduler section)

# 7. Service Account for the Archiver Scheduler Job
resource "google_service_account" "scheduler_archiver_invoker_sa" {
  project      = var.project_id
  account_id   = "scheduler-archiver-invoker" # Choose a unique ID
  display_name = "SA for Cloud Scheduler Archiver Invoker"
  description  = "Service account used by Cloud Scheduler to invoke the firestore-archiver-java function"
}
# 8. Grant Invoker Role to the Scheduler SA for the archiver function
resource "google_cloudfunctions2_function_iam_member" "scheduler_archiver_invoker_binding" {
  project        = google_cloudfunctions2_function.archiver_function.project
  location       = google_cloudfunctions2_function.archiver_function.location
  cloud_function = google_cloudfunctions2_function.archiver_function.name
  role           = "roles/cloudfunctions.invoker"
  # Use the same SA as the ingestor trigger scheduler for simplicity
  member     = "serviceAccount:${google_service_account.scheduler_archiver_invoker_sa.email}"
  depends_on = [google_cloudfunctions2_function.archiver_function]
}

resource "google_cloud_run_service_iam_member" "scheduler_cloud_run_archiver_invoker_binding" {
  project  = google_cloudfunctions2_function.archiver_function.project
  location = google_cloudfunctions2_function.archiver_function.location
  service  = google_cloudfunctions2_function.archiver_function.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.scheduler_archiver_invoker_sa.email}"

  # Ensure the function exists before trying to grant permission
  depends_on = [google_cloudfunctions2_function.ingestor_function]
}

# 9. Cloud Scheduler Job to Trigger the Archiver Function
resource "google_cloud_scheduler_job" "archiver_trigger_job" {
  project     = var.project_id
  region      = var.region
  name        = "firestore-archiver-trigger"
  description = "Triggers the Firestore archiver function daily"

  # Example: Run daily at 3:00 AM UTC
  schedule  = "0 2 * * *"
  time_zone = "Etc/UTC"

  http_target {
    uri         = google_cloudfunctions2_function.archiver_function.service_config[0].uri
    http_method = "POST" # Functions v2 HTTP triggers require POST for OIDC token
    body = "e30="
    oidc_token {
      # Use the same SA as the ingestor trigger scheduler for simplicity
      service_account_email = google_service_account.scheduler_archiver_invoker_sa.email
      audience              = google_cloudfunctions2_function.archiver_function.service_config[0].uri
    }
  }
  retry_config {
    max_backoff_duration = "3600s"
    max_doublings        = 5
    max_retry_duration   = "0s"
    min_backoff_duration = "5s"
    retry_count          = 0
  }

  attempt_deadline = "600s" # Allow more time than function timeout
  depends_on       = [google_cloudfunctions2_function_iam_member.scheduler_archiver_invoker_binding]
}

# --- End of Archive Function Resources ---
