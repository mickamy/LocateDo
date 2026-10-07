# `tofu output -json maps_api_keys`: dev goes into android/local.properties as MAPS_API_KEY; stg and prod into the
# ANDROID_MAPS_API_KEY secret of the android-stg and android-prod GitHub environments.
output "maps_api_keys" {
  value     = { for env, key in google_apikeys_key.maps_android : env => key.key_string }
  sensitive = true
}
