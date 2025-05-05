# terraform/variables.tf

variable "project_id" {
  description = "The GCP project ID."
  type        = string
  default     = "solarman-data-ingestor" # Your confirmed Project ID
}

variable "region" {
  description = "The primary GCP region for resources."
  type        = string
  default     = "us-central1" # Or choose another region if you prefer
}

variable "terraform_state_bucket_name" {
  description = "Globally unique name for the GCS bucket storing Terraform state."
  type        = string
  # Using project_id helps ensure uniqueness, but you might need to adjust
  # if this name is already taken globally.
  default = "tfstate-solarman-data-ingestor"
}

variable "archive_bucket_name" {
  description = "Globally unique name for the GCS bucket storing solar data archives."
  type        = string
  # Using project_id helps ensure uniqueness
  default = "solar-archive-solarman-data-ingestor"
}

variable "archive_bucket_storage_class" {
  description = "Storage class for the archive bucket (e.g., STANDARD, NEARLINE, COLDLINE)."
  type        = string
  default     = "NEARLINE"
}

variable "archive_bucket_lifecycle_coldline_days" {
  description = "Number of days after which to transition archive objects to COLDLINE. 0 disables."
  type        = number
  default     = 90 # Transition to Coldline after 90 days
}

variable "archive_bucket_lifecycle_delete_days" {
  description = "Number of days after which to delete archive objects. 0 disables."
  type        = number
  default     = 730 # Delete after 2 years (adjust as needed)
}

variable "firestore_location_id" {
  description = "Location ID for the Firestore database (must match region or multi-region)."
  # Example multi-regions: nam5 (North America), eur3 (Europe)
  # Keep it consistent with your primary region for simplicity unless needed otherwise.
  type    = string
  default = "us-central1" # Firestore locations can be slightly different than compute regions
}

variable "solarman_secret_name" {
  description = "Name for the Secret Manager secret holding the Solarman token."
  type        = string
  default     = "solarman-bearer-token"
}
