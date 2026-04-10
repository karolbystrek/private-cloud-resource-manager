data_dir = "/tmp/pcrm-nomad"
bind_addr = "0.0.0.0"
name = "nomad-server"
disable_update_check = true

server {
  enabled = true
  bootstrap_expect = 1
}

client {
  enabled = true
  network_interface = "eth0"

  cpu_total_compute = 4000
  memory_total_mb = 4096

  meta {
    "private-cloud-resource-manager.node_id" = "local-node-01"
  }
}
