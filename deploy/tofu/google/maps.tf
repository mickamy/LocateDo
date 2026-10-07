# One Maps / Places key per environment, accepted only from that build of the Android app: the package name plus
# the SHA-1 of every certificate that build can be signed with.
locals {
  android_apps = {
    dev = {
      package_name = "com.locatedo.locatedo.dev"
      # The Android Studio debug keystore on the development Mac.
      sha1_fingerprints = ["98a93a037558c9bbbfc2124bca92504ec251fd7b"]
    }
    stg = {
      package_name = "com.locatedo.locatedo.stg"
      # android/credentials/staging.jks
      sha1_fingerprints = ["f4d00df82e382f84b8e1762f20b207b9b5651a06"]
    }
    prod = {
      package_name = "com.locatedo.locatedo"
      # Play's app signing key, which signs what users install, then android/credentials/upload.jks.
      sha1_fingerprints = [
        "77260675f3cb2fa08b0967ef33e7222809f8be3f",
        "9ba7a0450d98b4b256e5253302664bbf198822a1",
      ]
    }
  }
}

resource "google_apikeys_key" "maps_android" {
  for_each     = local.android_apps
  project      = local.projects[each.key]
  name         = "maps-android"
  display_name = "Android Maps and Places"

  restrictions {
    android_key_restrictions {
      dynamic "allowed_applications" {
        for_each = each.value.sha1_fingerprints
        content {
          package_name     = each.value.package_name
          sha1_fingerprint = allowed_applications.value
        }
      }
    }
    api_targets {
      service = "maps-android-backend.googleapis.com"
    }
    api_targets {
      service = "places.googleapis.com"
    }
  }

  depends_on = [google_project_service.apikeys, google_project_service.maps, google_project_service.places]
}
