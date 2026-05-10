job "{{NOMAD_JOB_ID}}" {
  type = "batch"

  meta {
    job_id = "{{JOB_ID}}"
    run_id = "{{RUN_ID}}"
    user_id = "{{USER_ID}}"
    quota_reservation_id = "{{QUOTA_RESERVATION_ID}}"
    correlation_id = "{{CORRELATION_ID}}"
    resource_class = "{{RESOURCE_CLASS}}"
  }

  group "execution-group" {
    task "user-workload" {
      driver = "docker"

      config {
        image   = "{{DOCKER_IMAGE}}"
        command = "/bin/sh"
        args    = ["-c", "{{EXECUTION_COMMAND}}"]
      }

      env {
        JOB_ID = "{{JOB_ID}}"
        RUN_ID = "{{RUN_ID}}"
        USER_ID = "{{USER_ID}}"
        TRACE_ID = "{{TRACE_ID}}"
        CORRELATION_ID = "{{CORRELATION_ID}}"
        QUOTA_RESERVATION_ID = "{{QUOTA_RESERVATION_ID}}"
        RESOURCE_CLASS = "{{RESOURCE_CLASS}}"
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
        network_mode = "supabase_default"
        command = "/bin/sh"
        args    = ["-ec", "apk add --no-cache curl zip >/dev/null && if [ -d \"$NOMAD_ALLOC_DIR/data\" ] && [ \"$(ls -A \"$NOMAD_ALLOC_DIR/data\" 2>/dev/null)\" ]; then cd \"$NOMAD_ALLOC_DIR/data\" && zip -qr /tmp/output.zip .; fi && if [ -f /tmp/output.zip ]; then curl -fsS -X PUT --upload-file /tmp/output.zip \"$ARTIFACT_UPLOAD_URL\"; else echo \"No artifacts to upload, skipping\"; fi"]
      }

      env {
        JOB_ID = "{{JOB_ID}}"
        RUN_ID = "{{RUN_ID}}"
        USER_ID = "{{USER_ID}}"
        TRACE_ID = "{{TRACE_ID}}"
        CORRELATION_ID = "{{CORRELATION_ID}}"
        QUOTA_RESERVATION_ID = "{{QUOTA_RESERVATION_ID}}"
        RESOURCE_CLASS = "{{RESOURCE_CLASS}}"
        ARTIFACT_OBJECT_KEY = "{{ARTIFACT_OBJECT_KEY}}"
        ARTIFACT_UPLOAD_URL = "{{ARTIFACT_UPLOAD_URL}}"
      }
    }
  }
}
