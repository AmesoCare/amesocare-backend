#!/usr/bin/env bash
# Tear DOWN the Ameso POC infrastructure. Deletes the instance, EBS volume,
# Elastic IP, VPC, and everything else in the stack — charges stop completely.
#
#   ./infra/teardown.sh
#
# Optional env vars:  AWS_REGION (default us-east-1), STACK_NAME (default ameso-poc)
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
STACK="${STACK_NAME:-ameso-poc}"

echo "Deleting stack '$STACK' in $REGION — this removes the instance, EBS, and Elastic IP."
read -rp "Type the stack name to confirm: " CONFIRM
[ "$CONFIRM" = "$STACK" ] || { echo "Aborted."; exit 1; }

aws cloudformation delete-stack --region "$REGION" --stack-name "$STACK"
echo "Delete requested. Waiting for completion..."
aws cloudformation wait stack-delete-complete --region "$REGION" --stack-name "$STACK"
echo "Torn down. No further charges for this stack."
