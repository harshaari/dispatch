# Dispatch — M14 Internal API Security Contract

Dispatch operational endpoints under `/internal/**` require the `X-Internal-Token` header. The expected value comes from `INTERNAL_API_TOKEN`; local Compose supplies an explicit development value, while deployments must inject a secret. Health and metrics remain available for platform probes and scraping.
