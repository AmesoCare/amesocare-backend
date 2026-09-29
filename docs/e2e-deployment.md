# Personal AWS E2E deployment

- AWS account: `302747271861`; operator: `ameso_patient`.
- Region: `us-east-1`; instance: `i-0bf3ace8bd4957a21` (`t3a.medium`, 2 vCPU / 4 GiB RAM).
- Elastic IP: `32.195.149.214`; allocation: `eipalloc-0a3ed24fdfe4e381f`.
- Security group: `sg-0764b4d39c71defcb` (`amesocare-e2e`).
- Inbound SSH 22 and API ports 5001–5004 are restricted to `71.69.223.128/32`.
- Source: backend commit `3ac5ea5`, uploaded as an archive to `/home/ubuntu/amesocare-backend`.
- This instance was created directly with EC2, not the repository CloudFormation stack.

Connect from the developer computer:

```sh
ssh -i ~/.ssh/amesocare-e2e ubuntu@32.195.149.214
cd ~/amesocare-backend
sudo docker compose -f docker-compose.prod.yml ps
sudo docker compose -f docker-compose.prod.yml logs -f incident-service notification-service
```

Backend secrets are in `.env` on the server (mode 600), not in this repository.
WhatsApp credentials are empty: delivery uses mock logging. No hospital UI is
hosted on this VM. Point the local hospital UI to ports 5001–5004 on this IP.
The Android debug APK uses ports 5001–5003. Use the phone on the developer Wi-Fi;
cellular access requires a separately authorized source IP rule. If your public
IP changes, update the dedicated security group source CIDR.

The server holds an archive rather than a Git checkout. To update, upload a new
backend archive, extract it into the same directory without overwriting `.env`,
and run `sudo docker compose -f docker-compose.prod.yml up -d --build`.

Stop/start the VM from the local AWS CLI:

```sh
aws ec2 stop-instances --region us-east-1 --instance-ids i-0bf3ace8bd4957a21
aws ec2 start-instances --region us-east-1 --instance-ids i-0bf3ace8bd4957a21
```

Containers have `restart: unless-stopped`. After manually stopping containers,
start them again with `sudo docker compose -f docker-compose.prod.yml up -d`.
Do not run `down -v` unless you intend to delete database data.
The 40 GB encrypted root disk is retained if the instance is terminated.
The disk and Elastic IP continue to incur charges while the instance is stopped;
clean them up separately when permanently retiring the environment.

## Cost configuration

Resized from t3.large to t3a.medium. CPU credits are Standard (no surplus-credit
charges; CPU throttles if credits run out). Detailed monitoring remains disabled.
The existing 40 GB gp3 disk uses baseline 3,000 IOPS / 125 MiB/s. Data and the
Elastic IP are preserved.

Estimated US East (N. Virginia) on-demand costs at 730 hours/month: compute
$27.45, disk $3.20, IPv4 $3.65; total approximately $34.30/month before transfer,
taxes, and credits. Previous configuration was approximately $67.59/month.
Stop the instance using the command above between testing sessions to avoid
compute charges. Disk and IPv4 together remain approximately $6.85/month.
No automatic shutdown schedule has been configured.

Pricing references: https://aws.amazon.com/ec2/instance-types/t3/ ,
https://aws.amazon.com/ebs/pricing/ , https://aws.amazon.com/vpc/pricing/ .
