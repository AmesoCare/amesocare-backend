#!/usr/bin/env bash
# Spin UP the Ameso POC infrastructure (CloudFormation create/update).
#
#   KEY_NAME=my-keypair ./infra/deploy.sh
#
# Optional env vars:
#   AWS_REGION            default us-east-1
#   STACK_NAME            default ameso-poc
#   INSTANCE_TYPE         default t4g.medium
#   SSH_CIDR              default 0.0.0.0/0  (set to YOUR.IP/32)
#   GITHUB_TOKEN          PAT to auto-clone the private repo + start the app on boot
#   ENABLE_HTTPS          true = deploy behind Caddy + Let's Encrypt on sslip.io
#   WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID   passed into the app .env
set -euo pipefail
cd "$(dirname "$0")/.."

: "${KEY_NAME:?Set KEY_NAME to an existing EC2 key pair name}"
REGION="${AWS_REGION:-us-east-1}"
STACK="${STACK_NAME:-ameso-poc}"

aws cloudformation deploy \
  --region "$REGION" \
  --stack-name "$STACK" \
  --template-file infra/ec2-stack.yaml \
  --no-fail-on-empty-changeset \
  --parameter-overrides \
    KeyName="$KEY_NAME" \
    InstanceType="${INSTANCE_TYPE:-t4g.medium}" \
    SshCidr="${SSH_CIDR:-0.0.0.0/0}" \
    RepoUrl="${BACKEND_REPO_URL:-https://github.com/AmesoCare/amesocare-backend.git}" \
    UiRepoUrl="${UI_REPO_URL:-https://github.com/AmesoCare/amesocare-ui.git}" \
    GitHubToken="${GITHUB_TOKEN:-}" \
    EnableHttps="${ENABLE_HTTPS:-false}" \
    WhatsAppAccessToken="${WHATSAPP_ACCESS_TOKEN:-}" \
    WhatsAppPhoneNumberId="${WHATSAPP_PHONE_NUMBER_ID:-}"

echo
echo "=== Stack outputs ==="
aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK" \
  --query "Stacks[0].Outputs[].{Key:OutputKey,Value:OutputValue}" --output table

echo
echo "Tear down (stops ALL charges) when done:  ./infra/teardown.sh"
