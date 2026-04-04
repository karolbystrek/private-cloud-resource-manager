# Web Routes Plan: Private Cloud Resource Manager

This document outlines the planned URIs for the Next.js frontend, categorized by access level and domain.

## 1. Public Routes (Unauthenticated)
* `/login` - User authentication and session instantiation.
* `/signup` - New user registration and onboarding.

## 2. User Routes (Authenticated Student/Researcher)
* `/` (Dashboard) - High-level overview showing CU wallet balance, active jobs summary, and general node availability.
* `/jobs` - List view of the user's historical and active jobs with status indicators.
* `/jobs/new` - Submission form for new compute tasks (requires Docker image, command, CPU/RAM limits).
* `/jobs/[id]` - Detailed view of a specific job, including live execution logs and MinIO artifact download links.
* `/nodes` - List view of available physical laboratory machines and general hardware specs.
* `/nodes/[id]` - Detailed view of a specific node, showing its exact CPU/RAM architecture, active Nomad allocations, and current load.
* `/wallet` - User's credit registry, showing the append-only transaction history (leases, deductions, refunds).
* `/profile` - User's personal information and dashboard preferences.
* `/profile/settings` - Account configuration, security settings, and session management.

## 3. Administrator Routes (RBAC: Admin)
* `/admin` - System-wide administrative dashboard displaying aggregated cluster metrics and global CU usage.
* `/admin/users` - Directory of all registered users in the system.
* `/admin/users/[id]` - Detailed view of a specific user, enabling admins to change roles, view their specific jobs, or manually adjust their CU balances (Credit/Debit).
* `/admin/jobs` - Global view of all jobs across the cluster across all users.
* `/admin/jobs/[id]` - Detailed admin inspection of a specific job, including force-kill capabilities for rogue processes.
* `/admin/nodes` - Advanced infrastructure view showing Nomad cluster health.
* `/admin/nodes/[id]` - Administrative control over a specific worker node (e.g., capabilities to cordon, drain, or view raw sidecar metrics).
* `/admin/settings` - Global configuration for system parameters (e.g., default CU assignment on registration, CU cost rates).
