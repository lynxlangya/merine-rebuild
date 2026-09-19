FROM node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d
RUN npm install --global pnpm@11.10.0
WORKDIR /workspace
CMD ["sh", "-c", "pnpm install --frozen-lockfile && pnpm --filter @merine/web dev"]
