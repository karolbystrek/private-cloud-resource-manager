package com.pcrm.backend.nomad.http;

import com.pcrm.backend.jobs.dto.GpuRequirement;
import com.pcrm.backend.nomad.NomadDispatchRequest;
import com.pcrm.backend.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NomadHttpDispatchClientTest {

    @TempDir
    private Path tempDir;

    @Test
    void omitsGpuDeviceBlockWhenGpuIsNotRequested() throws Exception {
        var renderedHcl = client().renderJobHcl(request(GpuRequirement.disabled()));

        assertThat(renderedHcl).doesNotContain("device \"nvidia/gpu\"");
    }

    @Test
    void rendersNvidiaGpuCountOnly() throws Exception {
        var renderedHcl = client().renderJobHcl(request(new GpuRequirement(true, 2, "nvidia", null, null)));

        assertThat(renderedHcl).contains("device \"nvidia/gpu\"");
        assertThat(renderedHcl).contains("count = 2");
        assertThat(renderedHcl).doesNotContain("${device.attr.memory}");
        assertThat(renderedHcl).doesNotContain("${device.model}");
    }

    @Test
    void rendersMinimumGpuMemoryConstraint() throws Exception {
        var renderedHcl = client().renderJobHcl(request(new GpuRequirement(true, 1, "nvidia", 16, null)));

        assertThat(renderedHcl).contains("attribute = \"${device.attr.memory}\"");
        assertThat(renderedHcl).contains("operator  = \">=\"");
        assertThat(renderedHcl).contains("value     = \"16 GiB\"");
    }

    @Test
    void rendersGpuModelConstraint() throws Exception {
        var renderedHcl = client().renderJobHcl(request(new GpuRequirement(true, 1, "nvidia", null, "Tesla T4")));

        assertThat(renderedHcl).contains("attribute = \"${device.model}\"");
        assertThat(renderedHcl).contains("value     = \"Tesla T4\"");
    }

    @Test
    void rendersGpuModelAndMemoryConstraintsTogether() throws Exception {
        var renderedHcl = client().renderJobHcl(request(new GpuRequirement(true, 1, "nvidia", 24, "L40S")));

        assertThat(renderedHcl).contains("attribute = \"${device.attr.memory}\"");
        assertThat(renderedHcl).contains("value     = \"24 GiB\"");
        assertThat(renderedHcl).contains("attribute = \"${device.model}\"");
        assertThat(renderedHcl).contains("value     = \"L40S\"");
    }

    private NomadHttpDispatchClient client() throws Exception {
        var template = tempDir.resolve("job.hcl");
        Files.writeString(template, """
                job "{{NOMAD_JOB_ID}}" {
                  meta {
                    user_id = "{{USER_ID}}"
                    job_id = "{{JOB_ID}}"
                    trace_id = "{{TRACE_ID}}"
                    correlation_id = "{{CORRELATION_ID}}"
                  }

                  group "execution-group" {
                    task "user-workload" {
                      config {
                        image = "{{DOCKER_IMAGE}}"
                        args = ["{{EXECUTION_COMMAND}}"]
                      }

                      env {
                        {{ENV_VARS}}
                      }

                      resources {
                        cores = {{CORES}}
                        memory = {{MEMORY_MB}}
                {{GPU_DEVICE_BLOCK}}
                      }
                    }

                    task "artifact-uploader" {
                      config {
                        network_mode = "{{DOCKER_COMPOSE_NETWORK}}"
                      }

                      env {
                        ARTIFACT_OBJECT_KEY = "{{ARTIFACT_OBJECT_KEY}}"
                        ARTIFACT_UPLOAD_URL = "{{ARTIFACT_UPLOAD_URL}}"
                      }
                    }
                  }
                }
                """);

        var storageService = mock(StorageService.class);
        when(storageService.buildArtifactObjectKey(any(), any())).thenReturn("artifact.zip");
        when(storageService.generatePresignedUploadUrl(any(), any())).thenReturn("http://upload.example");

        return new NomadHttpDispatchClient(
                "http://nomad.example",
                template.toString(),
                "pcrm_default",
                storageService
        );
    }

    private NomadDispatchRequest request(GpuRequirement gpuRequirement) {
        var userId = UUID.randomUUID();
        var jobId = UUID.randomUUID();
        return new NomadDispatchRequest(
                userId,
                jobId,
                jobId.toString(),
                "ubuntu:24.04",
                "echo ok",
                2,
                4,
                gpuRequirement,
                Map.of(),
                UUID.randomUUID()
        );
    }
}
