#!/bin/bash

# OCI Instance Setup Script
# This script prepares an OCI free tier instance for running the ecommerce microservices

set -e

echo "🔧 Setting up OCI instance for ecommerce microservices..."

# Update system
echo "📦 Updating system packages..."
sudo apt-get update -y
sudo apt-get upgrade -y

# Install required packages
echo "📦 Installing required packages..."
sudo apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    git \
    htop \
    net-tools \
    vim

# Install Docker
echo "🐳 Installing Docker..."
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io

# Install Docker Compose
echo "🐳 Installing Docker Compose..."
sudo curl -L "https://github.com/docker/compose/releases/download/v2.23.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Add current user to docker group
echo "👤 Adding user to docker group..."
sudo usermod -aG docker $USER

# Configure Docker daemon for production
echo "⚙️ Configuring Docker daemon..."
sudo tee /etc/docker/daemon.json > /dev/null <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2"
}
EOF

sudo systemctl restart docker

# Configure system limits for production
echo "⚙️ Configuring system limits..."
sudo tee -a /etc/sysctl.conf > /dev/null <<EOF

# Increase system limits for microservices
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_tw_reuse = 1
fs.file-max = 2097152
vm.max_map_count = 262144
vm.swappiness = 10
EOF

sudo sysctl -p

# Configure firewall rules
echo "🔥 Configuring firewall..."
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT     # SSH
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT     # HTTP
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT    # HTTPS
sudo iptables -A INPUT -p tcp --dport 8888 -j ACCEPT   # API Gateway
sudo iptables -A INPUT -p tcp --dport 8761 -j ACCEPT   # Service Discovery
sudo iptables -A INPUT -p tcp --dport 3000 -j ACCEPT   # Grafana
sudo iptables -A INPUT -p tcp --dport 9090 -j ACCEPT   # Prometheus
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
sudo iptables -P INPUT DROP

# Save iptables rules
sudo apt-get install -y iptables-persistent
sudo netfilter-persistent save

# Create application directory
echo "📁 Creating application directory..."
mkdir -p ~/ecommerce-app

# Configure swap (important for 1GB RAM instance)
echo "💾 Configuring swap space..."
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Install monitoring tools
echo "📊 Installing monitoring tools..."
# Node exporter for system metrics
wget https://github.com/prometheus/node_exporter/releases/download/v1.7.0/node_exporter-1.7.0.linux-amd64.tar.gz
tar xvf node_exporter-1.7.0.linux-amd64.tar.gz
sudo cp node_exporter-1.7.0.linux-amd64/node_exporter /usr/local/bin/
rm -rf node_exporter-1.7.0.linux-amd64*

# Create systemd service for node exporter
sudo tee /etc/systemd/system/node_exporter.service > /dev/null <<EOF
[Unit]
Description=Node Exporter
After=network.target

[Service]
User=nobody
Group=nogroup
Type=simple
ExecStart=/usr/local/bin/node_exporter

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable node_exporter
sudo systemctl start node_exporter

# Create backup script
echo "💾 Creating backup script..."
cat > ~/ecommerce-app/backup.sh <<'EOF'
#!/bin/bash
BACKUP_DIR="/home/ubuntu/backups"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

# Backup PostgreSQL
docker exec ecommerce-postgres pg_dumpall -U postgres > $BACKUP_DIR/postgres_backup_$DATE.sql

# Backup volumes
docker run --rm -v ecommerce-microservices_postgres_data:/data -v $BACKUP_DIR:/backup alpine tar czf /backup/postgres_data_$DATE.tar.gz -C /data .

# Keep only last 7 days of backups
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete
find $BACKUP_DIR -name "*.tar.gz" -mtime +7 -delete
EOF
chmod +x ~/ecommerce-app/backup.sh

# Add backup cron job
(crontab -l 2>/dev/null; echo "0 2 * * * /home/ubuntu/ecommerce-app/backup.sh") | crontab -

echo "✅ OCI instance setup completed!"
echo ""
echo "📝 Next steps:"
echo "1. Log out and log back in for docker group changes to take effect"
echo "2. Copy your .env.production file to ~/ecommerce-app/"
echo "3. Copy docker-compose.prod.yml to ~/ecommerce-app/"
echo "4. Run the deployment script"
echo ""
echo "🔒 Security notes:"
echo "- Change the default passwords in your .env.production file"
echo "- Configure SSL/TLS for production use"
echo "- Review and adjust firewall rules as needed"