# terraform/outputs.tf

output "archive_bucket_name" {
  description = "Name of the GCS bucket for solar data archives."
  value       = google_storage_bucket.archive_bucket.name
}

output "archive_bucket_url" {
  description = "URL of the GCS bucket for solar data archives."
  value       = google_storage_bucket.archive_bucket.url
}

output "firestore_database_name" {
  description = "Resource name of the Firestore database."
  value       = google_firestore_database.database.name
}

output "solarman_secret_id" {
  description = "ID of the Secret Manager secret for the Solarman token."
  value       = google_secret_manager_secret.solarman_token.secret_id
}

output "solarman_secret_name" {
  description = "Full resource name of the Secret Manager secret."
  value       = google_secret_manager_secret.solarman_token.name
}
