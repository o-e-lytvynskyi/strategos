# Security policy

Report a vulnerability privately through GitHub: **Security → Report a vulnerability** on this
repository. Please do not open a public issue for it. You will get an answer as soon as the
owner can; this is a volunteer project without a response-time guarantee.

In scope: the mod's server and network code (a client that can crash or control a server, read
hidden match state, or run code), the build and CI (anything that runs untrusted code with
privileges), and the agent procedures (a way to make an unattended agent act against its rules).

How the project protects its agents, machines and dependencies: [docs/security.md](docs/security.md).
