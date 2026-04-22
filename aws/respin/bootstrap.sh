#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# bootstrap.sh — install all cluster-level tooling after `eksctl create cluster`
#
# Prerequisites (already handled by eksctl-cluster.yaml if cluster was created
# with that config):
#   - kubectl configured for the cluster
#   - IAM policy AWSLoadBalancerControllerIAMPolicy exists
#   - IRSA role AmazonEKSLoadBalancerControllerRole bound to
#     kube-system/aws-load-balancer-controller SA
#
# Installs (in order):
#   1. Gateway API CRDs v1.1.0
#   2. AWS Load Balancer Controller (kube-system)
#   3. NGINX Gateway Fabric 2.x (nginx-gateway) + GatewayClass
#   4. Argo Rollouts + Gateway API plugin (argo-rollouts)
#   5. ArgoCD (argocd)
#   6. kube-prometheus-stack (monitoring)
#   7. ECR pull secret / IAM policy for nodes (kubelet pulls from ECR using
#      node role — already granted by eksctl withAddonPolicies.imageBuilder)
#   8. ArgoCD Application → syncs trade-service/k8s/**  to trade-app namespace
#
# Idempotent: re-running is safe. All helm installs use --upgrade --install.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

CLUSTER_NAME="github-runners"
REGION="ap-south-1"
ACCOUNT_ID="748624205055"

echo "──> Verify kubectl context"
kubectl config current-context
kubectl get nodes

# ── 1. Gateway API CRDs ───────────────────────────────────────────────────────
echo "──> [1/7] Installing Gateway API CRDs v1.1.0"
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.1.0/standard-install.yaml

# ── 2. AWS Load Balancer Controller ───────────────────────────────────────────
echo "──> [2/7] Installing AWS Load Balancer Controller"
helm repo add eks https://aws.github.io/eks-charts >/dev/null 2>&1 || true
helm repo update >/dev/null

helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName="${CLUSTER_NAME}" \
  --set region="${REGION}" \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller \
  --wait --timeout 5m

# ── 3. NGINX Gateway Fabric ───────────────────────────────────────────────────
echo "──> [3/7] Installing NGINX Gateway Fabric"
helm upgrade --install ngf oci://ghcr.io/nginx/charts/nginx-gateway-fabric \
  --create-namespace -n nginx-gateway \
  --wait --timeout 5m

# GatewayClass is installed by the chart; verify
kubectl get gatewayclass nginx || kubectl apply -f - <<'EOF'
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: nginx
spec:
  controllerName: gateway.nginx.org/nginx-gateway-controller
EOF

# ── 4. Argo Rollouts + Gateway API plugin ─────────────────────────────────────
echo "──> [4/7] Installing Argo Rollouts + Gateway API plugin"
helm repo add argo https://argoproj.github.io/argo-helm >/dev/null 2>&1 || true
helm repo update >/dev/null

# Enable Gateway API plugin via trafficRouterPlugins in values
helm upgrade --install argo-rollouts argo/argo-rollouts \
  --create-namespace -n argo-rollouts \
  --set-string controller.trafficRouterPlugins[0].name="argoproj-labs/gatewayAPI" \
  --set-string controller.trafficRouterPlugins[0].location="https://github.com/argoproj-labs/rollouts-plugin-trafficrouter-gatewayapi/releases/download/v0.4.0/gatewayapi-plugin-linux-amd64" \
  --wait --timeout 5m

# ── 5. ArgoCD ─────────────────────────────────────────────────────────────────
echo "──> [5/7] Installing ArgoCD"
helm upgrade --install argocd argo/argo-cd \
  --create-namespace -n argocd \
  --set server.service.type=ClusterIP \
  --wait --timeout 5m

# ── 6. kube-prometheus-stack ──────────────────────────────────────────────────
echo "──> [6/7] Installing kube-prometheus-stack"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null

helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --create-namespace -n monitoring \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
  --wait --timeout 10m

# ── 7. ArgoCD Application → trade-service ─────────────────────────────────────
echo "──> [7/7] Applying ArgoCD Application for trade-service"
kubectl apply -f ../../trade-service/k8s/argocd/app.yaml

# Argo Rollouts plugin needs RBAC in trade-app namespace to patch HTTPRoute +
# manage its coordination ConfigMap. This is part of the repo manifests and
# ArgoCD will apply it, but we apply up-front so the first sync doesn't race.
kubectl apply -f ../../trade-service/k8s/rollouts/plugin-rbac.yaml

echo ""
echo "──────────────────────────────────────────────────────────────────────"
echo "Bootstrap complete."
echo ""
echo "Watch ArgoCD sync:"
echo "  kubectl -n argocd get application trade-service -w"
echo ""
echo "Get NLB hostname (once Gateway provisions it):"
echo "  kubectl -n trade-app get svc -l gateway.networking.k8s.io/gateway-name=sre-gateway"
echo ""
echo "ArgoCD admin password:"
echo "  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
echo "──────────────────────────────────────────────────────────────────────"
