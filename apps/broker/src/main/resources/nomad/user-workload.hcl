job "user-{{USER_ID}}-job-{{JOB_ID}}" {
  type = "batch"

  group "execution-group" {

    # task "lease-enforcer" {
    #   driver = "docker"
    #   lifecycle {
    #     hook    = "prestart"
    #     sidecar = true
    #   }
    #   config {
    #     image = "pcrm/lease-enforcer:latest"
    #   }
    #   env {
    #     JOB_ID = "{{JOB_ID}}"
    #   }
    # }

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

    # task "artifact-uploader" {
    #   driver = "docker"
    #   lifecycle {
    #     hook    = "poststop"
    #     sidecar = false
    #   }
    #   config {
    #     image = "pcrm/artifact-uploader:latest"
    #   }
    #   env {
    #     JOB_ID = "{{JOB_ID}}"
    #   }
    # }
  }
}
