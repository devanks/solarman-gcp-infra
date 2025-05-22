# terraform/backend.tf

terraform {
  backend "gcs" {
    bucket = "tfstate-solarman-data-ingestor" # MUST MATCH the bucket you just created
    prefix = "terraform/state"                # Path within the bucket to store state files
  }
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0" # Use a recent version
    }
  }
}
