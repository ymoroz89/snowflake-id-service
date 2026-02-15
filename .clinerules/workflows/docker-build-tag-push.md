# Docker Build, Tag, Login and Push Workflow

Build the Docker image, tag it as latest, login to Docker Hub, and push the image.

## Input

- `DOCKERHUB_USERNAME`: Your Docker Hub username

## Steps

1. **Build the Docker image** with the default tag:
   ```bash
   docker build -t snowflake-id-service:latest .
   ```

2. **Tag the image** for Docker Hub with your username:
   ```bash
   docker tag snowflake-id-service:latest {{DOCKERHUB_USERNAME}}/snowflake-id-service:latest
   ```

3. **Login to Docker Hub**:
   ```bash
   docker login -u {{DOCKERHUB_USERNAME}}
   ```

4. **Push the image** to Docker Hub:
   ```bash
   docker push {{DOCKERHUB_USERNAME}}/snowflake-id-service:latest
   ```

## Example

```bash
# Build
docker build -t snowflake-id-service:latest .

# Tag
docker tag snowflake-id-service:latest {{DOCKERHUB_USERNAME}}/snowflake-id-service:latest

# Login
docker login -u {{DOCKERHUB_USERNAME}}

# Push
docker push {{DOCKERHUB_USERNAME}}/snowflake-id-service:latest
```
