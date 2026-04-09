job "user#{{USER_ID}}-job#{{JOB_ID}}" {
  type = "batch"

  group "execution-group" {

    task "user-workload" {
      driver = "docker"
      config {
        image   = "{{DOCKER_IMAGE}}"
        command = "/bin/sh"
        args    = ["-c", "{{EXECUTION_COMMAND}}"]
      }

      # User environment variables injected directly here
      env {
        JOB_ID = "{{JOB_ID}}"
        {{ENV_VARS}}
      }

      resources {
        cores  = {{CORES}}
        memory = {{MEMORY_MB}}
      }
    }

    task "artifact-uploader" {
      driver = "docker"
      lifecycle {
        hook    = "poststop"
        sidecar = false
      }
      config {
        image   = "alpine:3.20"
        network_mode = "private-cloud-resource-manager_default"
        command = "/bin/sh"
        args    = ["-ec", "apk add --no-cache curl zip >/dev/null && if [ -d \"$NOMAD_ALLOC_DIR/data\" ] && [ \"$(ls -A \"$NOMAD_ALLOC_DIR/data\" 2>/dev/null)\" ]; then cd \"$NOMAD_ALLOC_DIR/data\" && zip -qr /tmp/output.zip .; fi && if [ -f /tmp/output.zip ]; then curl -fsS -X PUT --upload-file /tmp/output.zip \"$ARTIFACT_UPLOAD_URL\"; else echo \"No artifacts to upload, skipping\"; fi"]
      }
      env {
        JOB_ID = "{{JOB_ID}}"
        ARTIFACT_OBJECT_KEY = "{{ARTIFACT_OBJECT_KEY}}"
        ARTIFACT_UPLOAD_URL = "{{ARTIFACT_UPLOAD_URL}}"
      }
    }
  }
}
