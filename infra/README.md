# EC2 infrastructure

The existing CloudFormation template retains its resources, service ports, and
optional Caddy HTTPS setup. Bootstrap now clones `amesocare-backend` and
`amesocare-ui` alongside each other under `/home/ubuntu`, configures separate
`.env` files, starts the backend, then the UI, then optional Caddy.

```sh
KEY_NAME=my-keypair SSH_CIDR=YOUR.IP/32 GITHUB_TOKEN=your-token ./infra/deploy.sh
```

The token needs read access to both repositories. Without it, bootstrap installs
prerequisites only; clone and configure both repositories manually using the
root README. `ENABLE_HTTPS=true` retains the existing sslip.io HTTPS setup.
`BACKEND_REPO_URL` and `UI_REPO_URL` override the two Git URLs; existing options
`AWS_REGION`, `STACK_NAME`, `INSTANCE_TYPE`, `WHATSAPP_ACCESS_TOKEN`, and
`WHATSAPP_PHONE_NUMBER_ID` remain available. The template retains `RepoUrl`
for the backend and adds `UiRepoUrl` for the frontend repository.

Use the root README's migration steps for an already-running server. Updating
CloudFormation UserData does not migrate an existing checkout automatically.
`infra/teardown.sh` deletes the stack and its resources, as before.
