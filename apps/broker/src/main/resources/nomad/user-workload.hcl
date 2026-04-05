job "user-workload-template" {
  type = "batch"

  # This block allows the job to be invoked multiple times with different metadata
  parameterized {
    meta_required = ["DOCKER_IMAGE", "EXECUTION_COMMAND", "JOB_ID"]
  }

  group "execution-group" {
    # Task 1: The Guard/Lease (Starts before user workload)
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
    #     JOB_ID = "${NOMAD_META_JOB_ID}"
    #   }
    # }

    # Task 2: The actual user work
    task "user-workload" {
      driver = "docker"
      config {
        image   = NOMAD_META_DOCKER_IMAGE
        command = "/bin/sh"
        args    = ["-c", NOMAD_META_EXECUTION_COMMAND]
      }

      resources {
        # Using Cores instead of MHz
        cores  = 1
        memory = 1024
      }
    }

    # Task 3: The Cleanup/Uploader (Runs after user workload finishes)
    # task "artifact-uploader" {
    #   driver = "docker"
    #   lifecycle {
    #     hook    = "poststop"
    #     sidecar = false
    #   }
    #   config {
    #     image = "pcrm/artifact-uploader:latest"
    #   }
    # }
  }
}
