# Deploy on Oracle Cloud (Always Free)

This runs the app on one free Arm virtual machine, with HTTPS and a database that keeps your data.
Everything used is free and permissively licensed. You create the Oracle account yourself; it asks for a
card to verify identity, and nothing is charged while you stay inside the Always Free limits.

What you end up with: `https://<your-address>` → Caddy (HTTPS) → the app → PostgreSQL with pgvector.

## 1. Create the VM

1. In the Oracle Cloud console choose **Compute → Instances → Create instance**.
2. Image: **Canonical Ubuntu 24.04** (or 22.04). Shape: **Ampere A1 Flex** (marked *Always Free-eligible*), for example 2 OCPU and 12 GB. Stay within the Always Free totals shown on the page.
3. Networking: keep **Assign a public IPv4 address** on. Add your SSH public key and create the instance.
4. Note the **public IP**.

## 2. Open the web ports (two places)

1. **Cloud firewall:** instance → subnet → **Security List** → add ingress rules for TCP **80** and **443** from `0.0.0.0/0`.
2. **VM firewall:** Oracle's Ubuntu images block these ports too. On the VM:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo apt-get install -y iptables-persistent && sudo netfilter-persistent save
   ```

## 3. Install Docker and get the code

```bash
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
sudo usermod -aG docker $USER && newgrp docker
git clone https://github.com/2307shukla/ai-knowledge-assistant.git && cd ai-knowledge-assistant
```

## 4. Configure

```bash
cp deploy/oracle.env.example deploy/.env
```

Edit `deploy/.env`:

| Setting | What to put |
|---|---|
| `SITE_ADDRESS` | Your domain, or no domain: your IP with dashes plus `.sslip.io`, e.g. IP `203.0.113.10` → `203-0-113-10.sslip.io` |
| `POSTGRES_PASSWORD` | `openssl rand -hex 24` |
| `APP_ENCRYPTION_KEY` | `openssl rand -base64 32` (keep it: changing it makes saved personal API keys unreadable) |
| `AI_API_KEY` | Optional free Gemini key; empty means each user pastes their own in Settings |

## 5. Start

```bash
docker compose -f deploy/docker-compose.oracle.yml --env-file deploy/.env up -d --build
docker compose -f deploy/docker-compose.oracle.yml ps
```

The first build takes several minutes. Then open `https://<SITE_ADDRESS>`. The first request can take a few
seconds while Caddy obtains the certificate.

## Day-to-day

| Task | Command |
|---|---|
| Logs | `docker compose -f deploy/docker-compose.oracle.yml logs -f app` |
| Update to the latest code | `git pull && docker compose -f deploy/docker-compose.oracle.yml --env-file deploy/.env up -d --build` |
| Back up the database | `docker compose -f deploy/docker-compose.oracle.yml exec db pg_dump -U assistant assistant > backup.sql` |
| Stop | `docker compose -f deploy/docker-compose.oracle.yml down` (data stays in the volume) |

## Things to know

- **Idle VMs can be reclaimed.** Oracle may reclaim Always Free compute that stays almost idle for a long
  time. Its current Always Free documentation states the exact rule. Using the site now and then, or
  upgrading the account to pay-as-you-go while staying inside the free limits, avoids it; check Oracle's
  terms before relying on either.
- **Stay inside the free limits.** Do not pick a larger shape or add paid services.
- **Keep ports 80 and 443 only.** The database and app are not published; do not open other ports.
- **Secrets:** `deploy/.env` stays on the VM and is git-ignored. Never paste it into chat or commit it.
