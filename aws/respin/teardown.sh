#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# teardown.sh — destroy all AWS resources for this project
#
# Order matters. We delete in reverse of creation so that:
#   - LoadBalancer Services are gone BEFORE AWS LBC is uninstalled
#     (otherwise NLB + target groups get orphaned)
#   - OIDC provider + IAM are deleted AFTER the cluster (eksctl handles OIDC)
#
# Safe to re-run: each step tolerates "already deleted" / "not found".
# ──────────────────────────────────────────────────────────────────────────────
set -uo pipefail    # NOT -e: we want to continue past "already deleted"

CLUSTER_NAME="github-runners"
REGION="ap-south-1"
ACCOUNT_ID="748624205055"
ECR_REPO="trade-service"
LBC_POLICY_NAME="AWSLoadBalancerControllerIAMPolicy"
LBC_ROLE_NAME="AmazonEKSLoadBalancerControllerRole"

echo "================================================================"
echo "⚠  WILL DESTROY:"
echo "   - EKS cluster ${CLUSTER_NAME} (${REGION})"
echo "   - VPC + subnets (eksctl-created)"
echo "   - ECR repo ${ECR_REPO} and all images"
echo "   - IAM role ${LBC_ROLE_NAME}"
echo "   - IAM policy ${LBC_POLICY_NAME}"
echo "   - GitHub Actions OIDC provider (dedicated to this repo)"
echo "================================================================"
read -rp "Type 'destroy' to proceed: " CONFIRM
[ "$CONFIRM" = "destroy" ] || { echo "aborted"; exit 1; }

# ── Phase 1: remove app layer so NLB gets released ────────────────────────────
echo ""
echo "──> [1/6] Deleting app workloads (triggers NLB cleanup via AWS LBC)"
kubectl config use-context "arn:aws:eks:${REGION}:${ACCOUNT_ID}:cluster/${CLUSTER_NAME}" 2>/dev/null \
  || aws eks update-kubeconfig --region "${REGION}" --name "${CLUSTER_NAME}" 2>/dev/null \
  || echo "   (cluster unreachable — skipping k8s deletes)"

kubectl delete -f ../../trade-service/k8s/rollouts/ --ignore-not-found --wait=true --timeout=2m 2>/dev/null || true
kubectl delete -f ../../trade-service/k8s/gateway/  --ignore-not-found --wait=true --timeout=3m 2>/dev/null || true
kubectl delete -f ../../trade-service/k8s/base/     --ignore-not-found --wait=true --timeout=2m 2>/dev/null || true
kubectl delete -f ../../trade-service/k8s/argocd/   --ignore-not-found --wait=true --timeout=2m 2>/dev/null || true

echo "   Waiting 60s for NLB + target groups to drain..."
sleep 60

# Sanity: ensure no LBs remain tagged for this cluster
REMAINING=$(aws elbv2 describe-load-balancers --region "${REGION}" \
  --query "LoadBalancers[?contains(LoadBalancerName, 'k8s-tradeapp')].LoadBalancerArn" \
  --output text 2>/dev/null || true)
if [ -n "$REMAINING" ]; then
  echo "   ⚠  Orphan NLB(s) detected — deleting manually:"
  for arn in $REMAINING; do
    echo "      $arn"
    aws elbv2 delete-load-balancer --load-balancer-arn "$arn" --region "${REGION}" || true
  done
fi

# ── Phase 2: uninstall cluster add-ons ────────────────────────────────────────
echo ""
echo "──> [2/6] Uninstalling Helm releases"
helm uninstall kube-prometheus-stack      -n monitoring      2>/dev/null || true
helm uninstall argocd                     -n argocd          2>/dev/null || true
helm uninstall argo-rollouts              -n argo-rollouts   2>/dev/null || true
helm uninstall ngf                        -n nginx-gateway   2>/dev/null || true
helm uninstall aws-load-balancer-controller -n kube-system   2>/dev/null || true

# ── Phase 3: delete EKS cluster ───────────────────────────────────────────────
echo ""
echo "──> [3/6] Deleting EKS cluster (this takes ~15 minutes)"
eksctl delete cluster \
  --name "${CLUSTER_NAME}" \
  --region "${REGION}" \
  --disable-nodegroup-eviction \
  --wait

# ── Phase 4: IAM cleanup ──────────────────────────────────────────────────────
echo ""
echo "──> [4/6] Cleaning IAM"
# Role (eksctl should have removed the CFN stack, but just in case)
aws iam detach-role-policy \
  --role-name "${LBC_ROLE_NAME}" \
  --policy-arn "arn:aws:iam::${ACCOUNT_ID}:policy/${LBC_POLICY_NAME}" 2>/dev/null || true
aws iam delete-role --role-name "${LBC_ROLE_NAME}" 2>/dev/null || true

# Policy — must be detached from all entities first (eksctl role is gone now)
aws iam delete-policy \
  --policy-arn "arn:aws:iam::${ACCOUNT_ID}:policy/${LBC_POLICY_NAME}" 2>/dev/null || true

# GitHub Actions OIDC provider (dedicated to this repo, per decision)
OIDC_ARN=$(aws iam list-open-id-connect-providers \
  --query "OpenIDConnectProviderList[?contains(Arn, 'token.actions.githubusercontent.com')].Arn" \
  --output text 2>/dev/null || true)
if [ -n "$OIDC_ARN" ]; then
  echo "   Deleting GitHub Actions OIDC provider: $OIDC_ARN"
  aws iam delete-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" 2>/dev/null || true
fi

# ── Phase 5: ECR ──────────────────────────────────────────────────────────────
echo ""
echo "──> [5/6] Deleting ECR repo (images and all)"
aws ecr delete-repository \
  --repository-name "${ECR_REPO}" \
  --region "${REGION}" \
  --force 2>/dev/null || true

# ── Phase 6: verify ───────────────────────────────────────────────────────────
echo ""
echo "──> [6/6] Verification"
echo "Clusters:"
aws eks list-clusters --region "${REGION}" --output text 2>/dev/null || true
echo "Load balancers:"
aws elbv2 describe-load-balancers --region "${REGION}" \
  --query 'LoadBalancers[].LoadBalancerName' --output text 2>/dev/null || true
echo "ECR repos:"
aws ecr describe-repositories --region "${REGION}" \
  --query 'repositories[].repositoryName' --output text 2>/dev/null || true

echo ""
echo "================================================================"
echo "Teardown complete. Check AWS Cost Explorer tomorrow to confirm"
echo "no unexpected resources remain."
echo ""
echo "To re-spin: see aws/respin/README.md"
echo "================================================================"
