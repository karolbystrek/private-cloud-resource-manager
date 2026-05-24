# GPU-Aware Resource Specification Plan

## Summary

Implement GPU integration first, but model GPU as a device requirement, not just a count. A submitted job should describe the kind of GPU it needs: count, minimum VRAM, optional model, and optional scheduling preference. Nomad's NVIDIA plugin supports this through `device "nvidia/gpu"` with `count`, `constraint`, and `affinity`; it also exposes GPU attributes such as memory, power, driver version, clocks, PCI bandwidth, and runtime `NVIDIA_VISIBLE_DEVICES`.

References:

- NVIDIA plugin: <https://developer.hashicorp.com/nomad/plugins/devices/nvidia>
- Nomad device block: <https://developer.hashicorp.com/nomad/docs/job-specification/device>

## Implementation Order

### 1. Add GPU Device Requirements

Why: ML jobs need GPU placement on nodes with compatible GPU configuration, not merely "any N GPUs".

- Replace the simple `reqGpuCount` idea with a structured `gpuRequirement`:
  - `enabled: boolean`
  - `count: integer`
  - `vendor: "nvidia"` for v1
  - `minMemoryGb: integer | null`
  - `model: string | null`
  - `affinityModel: string | null`
- Persist these fields on `jobs`, either as explicit columns or a small JSON field. Prefer explicit columns for `enabled`, `count`, `vendor`, `min_memory_gb`, and `model`; affinity can be added later if needed.
- Update job submission, frontend form, proxy route, backend DTOs, job details, and history responses.
- Render Nomad HCL like:

```hcl
device "nvidia/gpu" {
  count = 1

  constraint {
    attribute = "${device.attr.memory}"
    operator  = ">="
    value     = "16 GiB"
  }

  constraint {
    attribute = "${device.model}"
    value     = "Tesla T4"
  }
}
```

- Omit the GPU device block when GPU is not requested.
- Do not allow raw HCL from users. Users submit structured fields only.
- Add environment/runtime documentation for GPU nodes: Linux, supported NVIDIA GPU, NVIDIA driver, `nvidia-smi`, Docker 19.03+, NVIDIA Container Toolkit, and Nomad NVIDIA device plugin enabled.

### 2. Track GPU Inventory Per Node

Why: Admins need to know what GPUs exist, and the dispatcher needs to know whether a GPU request is even plausible.

- Extend node sync to capture GPU devices from Nomad node details.
- Store per-device records, not only totals:
  - node id
  - device id
  - vendor
  - type
  - model
  - memory MiB
  - driver version
  - power
  - health/status if exposed
  - MIG/device instance identity if exposed by Nomad
- Add derived node totals:
  - total GPU count
  - total GPU memory MiB
  - GPU models present
- For MIG, treat each fingerprinted instance as its own schedulable GPU device, because the NVIDIA plugin fingerprints MIG instances individually.

### 3. Dispatch With GPU-Aware Eligibility

Why: Aggregate cluster GPU count is insufficient. A job requiring `2 x 24 GB GPU` must land on one eligible node with those devices.

- Before dispatch, check whether at least one available, non-draining, scheduling-eligible node can satisfy:
  - requested CPU
  - requested RAM
  - requested GPU count
  - requested GPU vendor
  - requested GPU model, if set
  - requested minimum GPU memory, if set
- Keep Nomad as the final scheduling authority; the local check only prevents obviously impossible dispatch attempts.
- Update queued-job selection so GPU jobs are not starved behind impossible jobs.
- Keep CPU/RAM-only jobs working exactly as today.

### 4. Weighted Quota Admission

Why: A GPU lease should cost more than a CPU-only lease, and larger GPUs should cost more than smaller GPUs.

- Keep one quota currency: weighted compute minutes.
- Compute resource units as:

```text
cpu_units = reqCpuCores
ram_units = ceil(reqRamGb / 4)

gpu_units =
  0 if no GPU
  count * gpu_weight_from_requirement

reserved_compute_minutes = lease_minutes * (cpu_units + ram_units + gpu_units)
```

- Define v1 GPU weights:
  - default NVIDIA GPU: `16`
  - GPU with `minMemoryGb >= 16`: `24`
  - GPU with `minMemoryGb >= 24`: `32`
  - GPU with `minMemoryGb >= 40`: `48`
- Apply the same weighted cost to initial lease, renewals, settlement, and usage ledger.
- Include the computed resource-unit breakdown in job details/admin views for auditability.

### 5. Add Role-Based Limits

Why: Users should request resources, but the platform must protect shared university infrastructure.

- Extend quota policy with role limits:
  - `max_job_cpu_cores`
  - `max_job_ram_gb`
  - `max_job_gpu_count`
  - `max_job_gpu_memory_gb`
  - `allowed_gpu_models` nullable list/JSON
- Defaults:
  - `STUDENT`: `4 CPU`, `16 GB RAM`, `1 GPU`, `16 GB GPU memory`, no model restriction
  - `EMPLOYEE`: `16 CPU`, `64 GB RAM`, `2 GPUs`, `40 GB GPU memory`, no model restriction
  - `ADMIN`: unlimited
- Enforce these limits server-side before admission.
- Return field-level validation errors when resource requests exceed role limits.
- Add admin UI/API support for changing role limits.

### 6. Admin Infrastructure Monitoring

Why: Admins need a clear view of actual capacity and utilization.

- Add admin node list/details data for:
  - total CPU/RAM/GPU
  - allocated CPU/RAM/GPU
  - available CPU/RAM/GPU
  - GPU models and memory per node
  - draining/scheduling eligibility
  - last successful Nomad sync
- Track allocated resources from active Nomad allocations, including device allocations where available.
- Show cluster totals and per-node breakdown.
- Highlight infrastructure issues:
  - GPU plugin missing
  - node has NVIDIA hardware but no visible Nomad GPU devices
  - node offline/stale
  - node ineligible/draining
  - no node can satisfy currently queued GPU jobs

## Public Interfaces And Types

- Job submission adds structured `gpuRequirement`.
- Job responses expose the stored GPU requirement and weighted resource cost.
- Node responses expose GPU device inventory and total/allocated/available resources.
- Admin quota policy APIs expose per-role CPU/RAM/GPU limits.
- Database migrations add job GPU requirement fields, GPU device inventory, node resource availability fields, and role-limit policy fields.

## Test Plan

- HCL rendering tests for:
  - no GPU
  - NVIDIA GPU count only
  - minimum GPU memory
  - exact GPU model
  - model plus memory
- Validation tests for malformed GPU requirements and role-limit violations.
- Quota tests for weighted CPU/RAM/GPU cost.
- Node sync tests for NVIDIA device inventory, including multiple models and MIG-like multiple instances.
- Dispatcher tests proving a GPU job only dispatches when one node can satisfy the whole GPU requirement.
- Admin API/UI tests for node resource totals and GPU inventory.
- Manual Nomad smoke test using an NVIDIA CUDA image and `nvidia-smi`.

## Assumptions

- v1 supports NVIDIA GPUs only.
- Users specify structured GPU requirements, not raw Nomad HCL.
- GPU model matching uses Nomad's `${device.model}` value exactly as reported by the NVIDIA plugin.
- GPU memory matching uses `${device.attr.memory}` with Nomad-supported units.
- Separate per-resource quotas are deferred; weighted compute minutes are sufficient for v1.
