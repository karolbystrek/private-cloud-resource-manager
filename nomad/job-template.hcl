job "{{NOMAD_JOB_ID}}" {
  type = "batch"

  meta {
    job_id = "{{JOB_ID}}"
    user_id = "{{USER_ID}}"
    correlation_id = "{{CORRELATION_ID}}"
  }

  group "execution-group" {
    task "user-workload" {
      driver = "docker"

      config {
        image   = "{{DOCKER_IMAGE}}"
        command = "/bin/sh"
        args    = ["-c", "mkdir -p \"$OUTPUT_DIR\" && {{EXECUTION_COMMAND}}"]
      }

      env {
        JOB_ID = "{{JOB_ID}}"
        USER_ID = "{{USER_ID}}"
        OUTPUT_DIR = "/alloc/data"
        TRACE_ID = "{{TRACE_ID}}"
        CORRELATION_ID = "{{CORRELATION_ID}}"
        {{ENV_VARS}}
      }

      resources {
        cores  = {{CORES}}
        memory = {{MEMORY_MB}}
{{GPU_DEVICE_BLOCK}}
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
        network_mode = "{{DOCKER_COMPOSE_NETWORK}}"
        command = "/bin/sh"
        args    = ["-ec", "apk add --no-cache curl zip >/dev/null && if [ -d \"$NOMAD_ALLOC_DIR/data\" ] && [ \"$(ls -A \"$NOMAD_ALLOC_DIR/data\" 2>/dev/null)\" ]; then cd \"$NOMAD_ALLOC_DIR/data\" && zip -qr /tmp/output.zip .; fi && if [ -f /tmp/output.zip ]; then curl -fsS -X PUT --upload-file /tmp/output.zip \"$ARTIFACT_UPLOAD_URL\"; else echo \"No artifacts to upload, skipping\"; fi"]
      }

      env {
        JOB_ID = "{{JOB_ID}}"
        USER_ID = "{{USER_ID}}"
        TRACE_ID = "{{TRACE_ID}}"
        CORRELATION_ID = "{{CORRELATION_ID}}"
        ARTIFACT_OBJECT_KEY = "{{ARTIFACT_OBJECT_KEY}}"
        ARTIFACT_UPLOAD_URL = "{{ARTIFACT_UPLOAD_URL}}"
      }
    }
  }
}
