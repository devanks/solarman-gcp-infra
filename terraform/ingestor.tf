# Cloud Function for ingestor

# --- Ingestor Function Resources ---
# --- Add this data source ---
data "archive_file" "ingestor_source_zip" {
  type = "zip"
  # Path to your function's source code directory, relative to this Terraform file
  source_dir = "../functions/ingestor"
  # Where Terraform will temporarily create the zip file before uploading
  # Using path.cwd ensures it's relative to where you run `terraform apply`
  output_path = "${path.cwd}/.terraform/build/solarman-ingestor-source-${timestamp()}.zip"

  # IMPORTANT: Exclude files and directories you DON'T want in the function deployment
  # This typically includes build artifacts, IDE configs, local secrets, etc.
  # Adjust this list based on your project structure.
  excludes = [
    "build/**",         # Gradle build output directory
    ".gradle/**",       # Gradle wrapper cache/files
    "bin/**",           # Sometimes used for compiled output (less common with Gradle)
    ".idea/**",         # IntelliJ IDEA project files
    "*.iml",            # IntelliJ IDEA module files
    ".DS_Store",        # macOS specific files
    "terraform/**",     # Don't include the terraform code itself if it's nested
    "*.log",            # Any log files
    "local.properties", # Example local config file
    ".env"              # Example environment file (secrets should be in Secret Manager)
    # Add any other patterns to exclude
  ]
}
# --- End of data source addition ---
# 1. Dedicated Service Account for the Ingestor Function
resource "google_service_account" "ingestor_function_sa" {
  project      = var.project_id
  account_id   = "solarman-ingestor-sa" # Choose a unique ID
  display_name = "SA for Solarman Ingestor Function"
  description  = "Service account used by the solarman-ingestor Cloud Function"
}

# 2. Upload Function ZIP (No changes needed)
resource "google_storage_bucket_object" "ingestor_function_source_zip" {
  # Use a name based on the archive file, maybe include a timestamp or hash for uniqueness if needed
  # Using the output_path's basename is usually sufficient if the bucket is dedicated
  name   = basename(data.archive_file.ingestor_source_zip.output_path)
  bucket = google_storage_bucket.function_code_bucket.name
  # Source is now the path to the zip file created by the archive_file data source
  source = data.archive_file.ingestor_source_zip.output_path

  # Ensure Terraform creates the archive before trying to upload it
  depends_on = [data.archive_file.ingestor_source_zip]
}

# 3. IAM Permissions for the *Dedicated* Service Account

# Grant Firestore User role to the new SA
resource "google_project_iam_member" "ingestor_sa_firestore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.ingestor_function_sa.email}" # Use new SA email
}

# 4. Grant Secret Manager Secret Accessor role ONLY for the specific Solarman token secret
resource "google_secret_manager_secret_iam_member" "ingestor_sa_specific_secret_accessor" {
  project   = google_secret_manager_secret.solarman_token.project
  secret_id = google_secret_manager_secret.solarman_token.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.ingestor_function_sa.email}"

  # Ensure the secret exists before trying to grant permission
  depends_on = [google_secret_manager_secret.solarman_token]
}

# Grant Logs Writer role to the new SA (recommended for explicit permissions)
resource "google_project_iam_member" "ingestor_sa_logs_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.ingestor_function_sa.email}" # Use new SA email
}

# 5. Cloud Function (2nd Gen) - Ingestor (Updated to use dedicated SA)
resource "google_cloudfunctions2_function" "ingestor_function" {
  project  = var.project_id
  name     = "solarman-ingestor"
  location = var.region

  build_config {
    runtime     = "java21"
    entry_point = "org.springframework.cloud.function.adapter.gcp.GcfJarLauncher"
    # environment_variables = {
    #   "GOOGLE_ENTRYPOINT" = "java -jar ${local.ingestor_jar_name}"
    # }
    source {
      storage_source {
        bucket = google_storage_bucket.function_code_bucket.name
        object = google_storage_bucket_object.ingestor_function_source_zip.name
      }
    }
  }

  service_config {
    max_instance_count = 3
    min_instance_count = 0
    available_memory   = "512Mi"
    timeout_seconds    = 120

    # >>> USE THE DEDICATED SERVICE ACCOUNT <<<
    service_account_email = google_service_account.ingestor_function_sa.email

    environment_variables = {
      MAIN_CLASS                                  = "dev.devanks.solarman.ingestor.IngestorApplication"
      LOGGING_LEVEL_DEV_DEVANKS_SOLARMAN_INGESTOR = "DEBUG"
      # Other overrides if needed
    }

    ingress_settings               = "ALLOW_INTERNAL_ONLY"
    all_traffic_on_latest_revision = true
  }

  # No event_trigger for HTTP function

  # Ensure the JAR is uploaded AND IAM roles for the NEW SA are assigned
  depends_on = [
    google_storage_bucket_object.ingestor_function_source_zip,
    google_project_iam_member.ingestor_sa_firestore_user,
    google_secret_manager_secret_iam_member.ingestor_sa_specific_secret_accessor,
    google_project_iam_member.ingestor_sa_logs_writer,
  ]

  lifecycle {
    ignore_changes = [labels]
  }
}

# --- End of Ingestor Function Resources ---

# --- Cloud Scheduler Resources ---

# 1. Service Account for the Scheduler Job
resource "google_service_account" "scheduler_invoker_sa" {
  project      = var.project_id
  account_id   = "scheduler-ingestor-invoker" # Choose a unique ID
  display_name = "SA for Cloud Scheduler Ingestor Invoker"
  description  = "Service account used by Cloud Scheduler to invoke the solarman-ingestor function"
}

# 2. Grant Invoker Role to the Scheduler SA for the specific function
resource "google_cloudfunctions2_function_iam_member" "scheduler_function_invoker_binding" {
  project        = google_cloudfunctions2_function.ingestor_function.project
  location       = google_cloudfunctions2_function.ingestor_function.location
  cloud_function = google_cloudfunctions2_function.ingestor_function.name
  role           = "roles/cloudfunctions.invoker"
  member         = "serviceAccount:${google_service_account.scheduler_invoker_sa.email}"

  # Ensure the function exists before trying to grant permission
  depends_on = [google_cloudfunctions2_function.ingestor_function]
}

resource "google_cloud_run_service_iam_member" "scheduler_cloud_run_invoker_binding" {
  project  = google_cloudfunctions2_function.ingestor_function.project
  location = google_cloudfunctions2_function.ingestor_function.location
  service  = google_cloudfunctions2_function.ingestor_function.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.scheduler_invoker_sa.email}"

  # Ensure the function exists before trying to grant permission
  depends_on = [google_cloudfunctions2_function.ingestor_function]
}

# 3. Cloud Scheduler Job to Trigger the Ingestor Function
resource "google_cloud_scheduler_job" "ingestor_trigger_job" {
  project     = var.project_id
  region      = var.region # Scheduler jobs are regional
  name        = "solarman-ingestor-trigger"
  description = "Triggers the solarman-ingestor function every 15 minutes"

  # Schedule using App Engine cron syntax: https://cloud.google.com/appengine/docs/standard/scheduling-jobs-with-cron-yaml#cron_yaml_The_schedule_format
  # This example runs every 15 minutes (0, 15, 30, 45 minutes past the hour)
  schedule  = "*/15 0-14 * * *"
  time_zone = "UTC" # Use UTC or your preferred timezone

  # Target the Cloud Function via HTTP
  http_target {
    # Use the function's HTTPS trigger URL (output from the function resource)
    uri = google_cloudfunctions2_function.ingestor_function.service_config[0].uri

    http_method = "GET"

    # Use OIDC token for authentication
    oidc_token {
      # The service account the scheduler job will run as
      service_account_email = google_service_account.scheduler_invoker_sa.email
      # The audience should be the URL being invoked. Terraform might infer this,
      # but explicitly setting it is safer.
      audience = google_cloudfunctions2_function.ingestor_function.service_config[0].uri
    }
    # Note: No body is needed for this Supplier function trigger
  }

  # Attempt deadline (how long Scheduler waits for a response before marking as failed)
  attempt_deadline = "180s" # Adjust based on function timeout + buffer

  # Ensure the invoker role is granted before creating the job
  depends_on = [google_cloudfunctions2_function_iam_member.scheduler_function_invoker_binding,
    google_cloud_run_service_iam_member.scheduler_cloud_run_invoker_binding,
  google_service_account.scheduler_invoker_sa]
}

# --- End of Cloud Scheduler Resources ---