# AWS Teardown & Re-Spin

Artifacts in this directory let you **destroy** the AWS deployment and **re-create** it from scratch with minimal manual steps.

## Contents

| File | Purpose |
|---|---|
| [iam-policy-alb-controller.json](iam-policy-alb-controller.json) | IAM policy for AWS Load Balancer Controller (NLB provisioning) |
| [eksctl-cluster.yaml](eksctl-cluster.yaml) | Cluster config — EKS + VPC + managed node group + OIDC + IRSA |
| [bootstrap.sh](bootstrap.sh) | Installs Gateway API CRDs, AWS LBC, NGF, Argo Rollouts + plugin, ArgoCD, kube-prometheus-stack, then applies ArgoCD Application |
| [teardown.sh](teardown.sh) | Destroys everything: workloads → NLB → addons → cluster → IAM → ECR → OIDC |

## Prerequisites on your laptop

- `aws` CLI authenticated to account **748624205055** (`aws sts get-caller-identity`)
- `eksctl` ≥ 0.170
- `kubectl` ≥ 1.30
- `helm` ≥ 3.14

## Re-spin (from zero to running app)

```bash
cd aws/respin

# 1. Create IAM policy (1 min)
aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam-policy-alb-controller.json

# 2. Create cluster + VPC + OIDC + IRSA (15-20 min)
eksctl create cluster -f eksctl-cluster.yaml

# 3. Install all controllers + ArgoCD app (5-10 min)
./bootstrap.sh

# 4. (Re-)build and push image — this is normally done by the pipeline,
#    but on first run the ECR repo is empty. Trigger a commit to main
#    or run the deploy.yml workflow manually. ArgoCD auto-syncs within
#    3 minutes once the image is in ECR and manifests point to it.
```

**Total time: ~30–45 min** (most of it is `eksctl create cluster`).

## Teardown

```bash
cd aws/respin
./teardown.sh   # prompts for confirmation; type 'destroy'
```

Destroys (in order):
1. App workloads → releases NLB cleanly
2. Helm releases (kube-prom-stack, argocd, argo-rollouts, NGF, AWS LBC)
3. EKS cluster (eksctl — removes VPC, subnets, node group, OIDC, CFN stacks)
4. IAM role `AmazonEKSLoadBalancerControllerRole`
5. IAM policy `AWSLoadBalancerControllerIAMPolicy`
6. GitHub Actions OIDC provider (dedicated to this repo per decision)
7. ECR repo `trade-service` + all images

Safe to re-run — each step tolerates "already deleted".

## What survives teardown (none, by design)

Per the current decision, everything AWS-side is destroyed. The only persistence is:
- **Git repo** — all manifests, pipeline, and these re-spin artifacts
- **GitHub Secrets** — `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (reusable for re-spin)

## Notes

- The `eksctl-cluster.yaml` assumes the IAM policy already exists. If you run `eksctl` before `aws iam create-policy`, cluster creation fails on the IRSA SA step. The re-spin order above handles this.
- `bootstrap.sh` must be run from `aws/respin/` so its relative paths to `../../trade-service/k8s/...` resolve.
- If the ArgoCD `Application` fails initial sync because the image tag in `canary.yaml` / `bluegreen.yaml` points to an image that was deleted with the ECR repo, re-run the GitHub Actions pipeline once to push a fresh image and update the tag.
